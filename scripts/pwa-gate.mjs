#!/usr/bin/env node
// Lighthouse PWA-installability gate for the storefront (ARCHITECTURE.md §4.1).
//
// Starts the built storefront, runs Lighthouse against it, and fails if the app
// has stopped being installable. Run it from the repo root AFTER a production
// build of the storefront:
//
//   pnpm --filter @town-basket/storefront build
//   node scripts/pwa-gate.mjs
//
// The production build matters: Serwist is disabled in development
// (next.config.js), so a dev server has no service worker and could never pass.
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
const STOREFRONT = path.join(REPO_ROOT, 'apps', 'storefront');

const PORT = Number(process.env.PWA_GATE_PORT ?? 3210);
const ORIGIN = `http://localhost:${PORT}`;

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
async function waitForServer(url, timeoutMs = 60_000) {
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
  throw new Error(`storefront did not start on ${url} within ${timeoutMs}ms`);
}

function run(command, args, options = {}) {
  return spawn(command, args, { stdio: 'inherit', ...options });
}

/**
 * Assemble the standalone output into a runnable server, the way the
 * production image does.
 *
 * <p>`output: 'standalone'` writes a self-contained server but deliberately
 * leaves out the static assets and `public/`, because a real deployment often
 * serves those from a CDN. apps/storefront/Dockerfile copies both in next to
 * the server; this does the same in place, so the gate measures the artifact
 * that actually ships.
 *
 * <p>Without this the app HTML would still be served, but every JS chunk,
 * `/sw.js` and every icon would 404 — the service worker would never register
 * and the gate would fail for a reason that has nothing to do with the code.
 */
async function prepareStandalone(root) {
  await cp(path.join(STOREFRONT, '.next', 'static'), path.join(root, '.next', 'static'), {
    recursive: true,
  });
  await cp(path.join(STOREFRONT, 'public'), path.join(root, 'public'), {
    recursive: true,
  });
}

async function main() {
  // The standalone tree mirrors the workspace layout, so the server lands at
  // .next/standalone/apps/storefront/server.js.
  const standaloneRoot = path.join(
    STOREFRONT,
    '.next',
    'standalone',
    'apps',
    'storefront',
  );
  const serverEntry = path.join(standaloneRoot, 'server.js');

  if (!existsSync(serverEntry)) {
    throw new Error(
      'apps/storefront/.next/standalone is missing — run ' +
        '`pnpm --filter @town-basket/storefront build` first',
    );
  }

  await prepareStandalone(standaloneRoot);

  const workDir = await mkdtemp(path.join(tmpdir(), 'tb-pwa-gate-'));
  const reportPath = path.join(workDir, 'lighthouse.json');

  // The standalone server, exactly as the Dockerfile's CMD runs it — not
  // `next start`, which Next warns is not supported alongside
  // `output: 'standalone'`.
  //
  // `detached` so the whole process group can be signalled, and the port is
  // ours rather than the app's default 3000, so the gate cannot collide with a
  // dev server someone has running.
  const server = run('node', [serverEntry], {
    cwd: standaloneRoot,
    env: { ...process.env, PORT: String(PORT), NODE_ENV: 'production' },
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
    await waitForServer(ORIGIN);

    // The storefront calls the API during SSR. There is no API in this job and
    // it does not need one: the page renders its empty states and stays
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
          ORIGIN,
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
      throw new Error(`lighthouse exited with code ${exitCode}`);
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
        console.log(`PASS  ${id}`);
        continue;
      }
      failures.push(
        `${id}: ${audit.explanation ?? audit.title ?? 'failed'}`.trim(),
      );
    }

    if (failures.length > 0) {
      console.error('\nThe storefront is no longer installable as a PWA:\n');
      for (const failure of failures) console.error(`  FAIL  ${failure}`);
      console.error(
        '\nSee apps/storefront/app/manifest.ts, app/sw.ts and public/icons/.',
      );
      process.exitCode = 1;
      return;
    }

    console.log('\nStorefront is installable — PWA gate passed.');
  } finally {
    stopServer();
    await rm(workDir, { recursive: true, force: true });
  }
}

main().catch((error) => {
  console.error(`pwa-gate: ${error.message}`);
  process.exitCode = 1;
});
