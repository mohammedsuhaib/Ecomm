/// <reference lib="webworker" />

// Service worker source compiled by @serwist/next (see next.config.js).
//
// ---------------------------------------------------------------------------
// The admin worker caches the SHELL and NOTHING ELSE.
//
// This is the opposite posture to the storefront's, and deliberately so. The
// storefront caches the catalogue because a shopper seeing a slightly stale
// price is a small, recoverable problem. Staff are not shoppers: they act on
// what this dashboard shows. A cached order queue could have someone pick an
// order the customer cancelled, or hand a parcel to the wrong rider; cached
// stock could have them accept an order for something already sold out. So no
// API response is ever read from a cache — every one goes to the network, and
// when the network is down the app says so instead of inventing an answer.
// This is the same rule the rider app states in its own worker.
//
// That is affordable here because NONE of the live data is in the HTML. The
// admin page is a client-rendered shell (app/page.tsx renders AdminShell ->
// LoginGate); every order, product and stock figure arrives later over the
// API. So the document, the JS and the CSS are static build output, safe to
// precache, and caching them is what makes the installed app open instantly on
// a shop laptop or tablet with poor Wi-Fi.
//
// It is also why the runtime rules below do NOT include Serwist's
// `defaultCache`: its last rule matches every cross-origin request with a
// NetworkFirst cache, and the API IS cross-origin here (it is a separate host
// — see NEXT_PUBLIC_API_BASE_URL). Spreading it in would quietly cache the
// order queue, which is the one thing this worker must never do.
//
// A second benefit, for a machine on the shop floor that staff share: no
// customer orders, addresses or phone numbers are left sitting in Cache
// Storage after the browser is closed.
// ---------------------------------------------------------------------------
//
// `self.__SW_MANIFEST` is replaced at build time by the Serwist webpack plugin
// with the list of precached app-shell URLs.

import type { PrecacheEntry, RuntimeCaching, SerwistGlobalConfig } from 'serwist';
import {
  CacheFirst,
  CacheableResponsePlugin,
  ExpirationPlugin,
  NetworkFirst,
  Serwist,
} from 'serwist';

declare global {
  interface WorkerGlobalScope extends SerwistGlobalConfig {
    __SW_MANIFEST: (PrecacheEntry | string)[] | undefined;
  }
}

declare const self: ServiceWorkerGlobalScope & {
  __SW_MANIFEST: (PrecacheEntry | string)[] | undefined;
};

/**
 * Next's build output: hashed filenames, so a given URL's content never
 * changes and it can be served from cache without revalidating. A deploy
 * produces new hashes and a new precache manifest, which is what retires the
 * old entries.
 */
const buildAssets: RuntimeCaching = {
  matcher: ({ url, sameOrigin }) =>
    sameOrigin && url.pathname.startsWith('/_next/static/'),
  handler: new CacheFirst({
    cacheName: 'tb-admin-build',
    plugins: [
      new ExpirationPlugin({ maxEntries: 200, maxAgeSeconds: 60 * 60 * 24 * 30 }),
      new CacheableResponsePlugin({ statuses: [0, 200] }),
    ],
  }),
};

/**
 * The app's own static files — the logo and the install icons. Unhashed, so
 * revalidate rather than trusting the cache forever, but serve from cache
 * first when offline.
 */
const staticAssets: RuntimeCaching = {
  matcher: ({ url, request, sameOrigin }) =>
    sameOrigin &&
    request.method === 'GET' &&
    /\.(?:png|jpg|jpeg|svg|ico|webp|woff2?)$/i.test(url.pathname),
  handler: new NetworkFirst({
    cacheName: 'tb-admin-static',
    networkTimeoutSeconds: 3,
    plugins: [
      new ExpirationPlugin({ maxEntries: 60, maxAgeSeconds: 60 * 60 * 24 * 30 }),
      new CacheableResponsePlugin({ statuses: [0, 200] }),
    ],
  }),
};

/**
 * The shell document.
 *
 * <p>Network first so a deploy is picked up on the next load, with the cached
 * copy as the offline fallback. Safe to cache only because the HTML carries no
 * operational data — see the header. The 3-second timeout is what makes a
 * flaky shop connection open the app rather than hang on a white screen.
 */
const shell: RuntimeCaching = {
  matcher: ({ request, sameOrigin }) =>
    sameOrigin && request.mode === 'navigate',
  handler: new NetworkFirst({
    cacheName: 'tb-admin-shell',
    networkTimeoutSeconds: 3,
    plugins: [
      new ExpirationPlugin({ maxEntries: 10, maxAgeSeconds: 60 * 60 * 24 * 7 }),
      new CacheableResponsePlugin({ statuses: [0, 200] }),
    ],
  }),
};

const serwist = new Serwist({
  precacheEntries: self.__SW_MANIFEST,
  skipWaiting: true,
  clientsClaim: true,
  navigationPreload: true,
  // Note what is NOT here: any rule matching the API. Nothing matches those
  // requests, so the worker never calls respondWith for them and the browser
  // fetches them itself — no cache, and no service worker sitting in the
  // middle of the order queue's SSE stream (/admin/orders/stream), which is a
  // long-lived response that must not be buffered or replayed.
  runtimeCaching: [shell, buildAssets, staticAssets],
  fallbacks: {
    entries: [
      {
        url: '/offline',
        matcher: ({ request }) => request.destination === 'document',
      },
    ],
  },
});

serwist.addEventListeners();
