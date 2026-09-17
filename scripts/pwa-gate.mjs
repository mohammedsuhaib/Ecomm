#!/usr/bin/env node
// Lighthouse PWA-installability gate for the installable frontends
// (ARCHITECTURE.md §4.1).
//
// Starts each app's production build, runs Lighthouse against it, and fails if
// the app has stopped being installable. Run it from the repo root AFTER
// building the apps you want checked:
//
//   pnpm --filter @town-basket/storefront build
//   pnpm --filter @town-basket/admin build
//   node scripts/pwa-gate.mjs             # every app below
//   node scripts/pwa-gate.mjs admin       # just one
//
// The production build matters: Serwist is disabled in development (each app's
// next.config.js), so a dev server has no service worker and could never pass.
//
// ---------------------------------------------------------------------------
// Why Lighthouse is PINNED to 11.x
//
// Lighthouse 12 REMOVED the PWA category outright, and with it the
// `installable-manifest` audit this gate is built on — 13.x has no PWA
// category and no installability audit of any kind. 11.7.1 is the last release
// that can answer "is this installable?", so the version is pinned rather than
// floating; a plain `npx lighthouse` would silently stop checking anything.
//
// It is fetched at run time instead of being a workspace dependency so the
// ~90 packages it drags in stay out of every contributor's `pnpm install`.
// If Chrome ever breaks this frozen version, the replacement is Chrome's own
// installability criteria over CDP, not a newer Lighthouse.
// ---------------------------------------------------------------------------

import { spawn } from 'node:child_process';
import { cp, mkdtemp, readFile, rm } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const LIGHTHOUSE_VERSION = '11.7.1';

const REPO_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

/**
 * The installable apps, and the port each is checked on.
 *
 * <p>The ports are this script's own, not the apps' usual ones, so a gate run
 * never collides with a dev server someone has open.
 *
 * <p>All three frontends are here, including the rider app, whose worker is
 * hand-written and push-only. It passes: Chrome's installability check is about
 * the manifest, the icons and a secure origin — it has not required a service
 * worker with a fetch handler for some years — so a caching worker is what
 * makes an app fast offline, not what makes it installable.
 */
const APPS = {
  storefront: { pkg: '@town-basket/storefront', port: 3210 },
  admin: { pkg: '@town-basket/admin', port: 3211 },
  delivery: { pkg: '@town-basket/delivery', port: 3212 },
};

/**
 * The audits that have to pass.
 *
 * `installable-manifest` is the gate ARCHITECTURE.md asks for — it is
 * Chrome's own installability verdict, covering the manifest, the icons and a
 * service worker with a fetch handler. The rest are the cheap
 * regressions that would degrade an installed launch without breaking it:
 * a missing maskable icon leaves Android to letterbox our icon into a white
 * circle, and a dropped theme colour or viewport shows up the first time
 * someone opens the installed app.
 */
const REQUIRED_AUDITS = [
  'installable-manifest',
  'maskable-icon',
  'splash-screen',
  'themed-omnibox',
  'viewport',
  'content-width',
];

/** Wait for the server to answer, or give up. */
async function waitForServer(name, url, timeoutMs = 60_000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    try {
      const response = await fetch(url, { signal: AbortSignal.timeout(3000) });
      if (response.ok) return;
    } catch {
      /* not up yet */
    }
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  throw new Error(`${name} did not start on ${url} within ${timeoutMs}ms`);
}

function run(command, args, options = {}) {
  return spawn(command, args, { stdio: 'inherit', ...options });
}

/**
 * Assemble an app's standalone output into a runnable server, the way its
 * production image does.
 *
 * <p>`output: 'standalone'` writes a self-contained server but deliberately
 * leaves out the static assets and `public/`, because a real deployment often
 * serves those from a CDN. Each app's Dockerfile copies both in next to the
 * server; this does the same in place, so the gate measures the artifact that
 * actually ships.
 *
 * <p>Without this the app HTML would still be served, but every JS chunk,
 * `/sw.js` and every icon would 404 — the service worker would never register
 * and the gate would fail for a reason that has nothing to do with the code.
 */
async function prepareStandalone(appDir, standaloneRoot) {
  await cp(
    path.join(appDir, '.next', 'static'),
    path.join(standaloneRoot, '.next', 'static'),
    { recursive: true },
  );
  await cp(path.join(appDir, 'public'), path.join(standaloneRoot, 'public'), {
    recursive: true,
  });
}

/** Gate one app. Returns the list of failures (empty means it passed). */
async function gate(name, { pkg, port }) {
  const appDir = path.join(REPO_ROOT, 'apps', name);
  // The standalone tree mirrors the workspace layout, so the server lands at
  // .next/standalone/apps/<name>/server.js.
  const standaloneRoot = path.join(appDir, '.next', 'standalone', 'apps', name);
  const serverEntry = path.join(standaloneRoot, 'server.js');
  const origin = `http://localhost:${port}`;

  if (!existsSync(serverEntry)) {
    return [
      `apps/${name}/.next/standalone is missing — run \`pnpm --filter ${pkg} build\` first`,
    ];
  }

  await prepareStandalone(appDir, standaloneRoot);

  const workDir = await mkdtemp(path.join(tmpdir(), `tb-pwa-gate-${name}-`));
  const reportPath = path.join(workDir, 'lighthouse.json');

  // The standalone server, exactly as the Dockerfile's CMD runs it — not
  // `next start`, which Next warns is not supported alongside
  // `output: 'standalone'`.
  //
  // `detached` so the whole process group can be signalled.
  const server = run('node', [serverEntry], {
    cwd: standaloneRoot,
    env: { ...process.env, PORT: String(port), NODE_ENV: 'production' },
    detached: true,
  });

  const stopServer = () => {
    try {
      process.kill(-server.pid, 'SIGKILL');
    } catch {
      /* already gone */
    }
  };

  try {
    await waitForServer(name, origin);

    // Both apps call the API from the browser. There is no API in this job and
    // neither needs one: they render their signed-out / empty states and stay
    // installable, which is exactly what this gate measures.
    const chromeFlags = [
      '--headless=new',
      // Required on CI runners and in containers, which have no user namespace
      // for Chrome's sandbox to use.
      '--no-sandbox',
      '--disable-dev-shm-usage',
    ].join(' ');

    const exitCode = await new Promise((resolve, reject) => {
      const lighthouse = run(
        'npx',
        [
          '--yes',
          `lighthouse@${LIGHTHOUSE_VERSION}`,
          origin,
          '--only-categories=pwa',
          '--output=json',
          `--output-path=${reportPath}`,
          `--chrome-flags=${chromeFlags}`,
          '--quiet',
        ],
        { cwd: REPO_ROOT },
      );
      lighthouse.on('error', reject);
      lighthouse.on('close', resolve);
    });

    if (exitCode !== 0) {
      return [`lighthouse exited with code ${exitCode}`];
    }

    const report = JSON.parse(await readFile(reportPath, 'utf8'));
    const failures = [];

    for (const id of REQUIRED_AUDITS) {
      const audit = report.audits?.[id];
      if (!audit) {
        // A missing audit means the pinned Lighthouse no longer ships it —
        // treat that as a failure rather than quietly gating on nothing.
        failures.push(`${id}: audit not present in the Lighthouse report`);
        continue;
      }
      if (audit.score === 1) {
        console.log(`  PASS  ${id}`);
        continue;
      }
      failures.push(`${id}: ${audit.explanation ?? audit.title ?? 'failed'}`.trim());
    }

    return failures;
  } finally {
    stopServer();
    await rm(workDir, { recursive: true, force: true });
  }
}

async function main() {
  const requested = process.argv.slice(2);
  for (const name of requested) {
    if (!APPS[name]) {
      throw new Error(
        `unknown app "${name}" — expected one of: ${Object.keys(APPS).join(', ')}`,
      );
    }
  }
  const names = requested.length > 0 ? requested : Object.keys(APPS);

  const failed = [];
  for (const name of names) {
    console.log(`\n── ${name} ──`);
    // Sequential, not parallel: two Chrome instances on one CI runner compete
    // for the same limited memory, and Lighthouse's own timings get noisy.
    const failures = await gate(name, APPS[name]);
    if (failures.length > 0) {
      failed.push({ name, failures });
    }
  }

  if (failed.length > 0) {
    console.error('\nNo longer installable as a PWA:\n');
    for (const { name, failures } of failed) {
      for (const failure of failures) console.error(`  FAIL  ${name}: ${failure}`);
    }
    console.error(
      '\nSee each app’s app/manifest.ts, app/sw.ts and public/icons/.',
    );
    process.exitCode = 1;
    return;
  }

  console.log(`\nInstallable — PWA gate passed for: ${names.join(', ')}.`);
}

main().catch((error) => {
  console.error(`pwa-gate: ${error.message}`);
  process.exitCode = 1;
});
