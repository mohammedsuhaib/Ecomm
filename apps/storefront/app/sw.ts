/// <reference lib="webworker" />

// Service worker source compiled by @serwist/next (see next.config.js).
//
// Strategy (ARCHITECTURE.md §4.1):
//  - precache the built app shell (Serwist injects the manifest below),
//  - stale-while-revalidate for catalogue GET requests (categories/products),
//  - navigation fallback to /offline when offline and uncached.
//
// `self.__SW_MANIFEST` is replaced at build time by the Serwist webpack plugin
// with the list of precached app-shell URLs.

import { defaultCache } from '@serwist/next/worker';
import type { PrecacheEntry, RuntimeCaching, SerwistGlobalConfig } from 'serwist';
import {
  CacheableResponsePlugin,
  ExpirationPlugin,
  Serwist,
  StaleWhileRevalidate,
} from 'serwist';

declare global {
  interface WorkerGlobalScope extends SerwistGlobalConfig {
    __SW_MANIFEST: (PrecacheEntry | string)[] | undefined;
  }
}

declare const self: ServiceWorkerGlobalScope & {
  __SW_MANIFEST: (PrecacheEntry | string)[] | undefined;
};

const API_BASE =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080/api/v1';

// Catalogue reads: stale-while-revalidate so browse/search feel instant and
// refresh in the background. Matches GET requests to the API's catalogue
// endpoints (categories, products, store).
const catalogueCaching: RuntimeCaching = {
  matcher: ({ url, request }) => {
    if (request.method !== 'GET') return false;
    const isApi = url.href.startsWith(API_BASE);
    const isCatalogue = /\/(categories|products|store)(\/|\?|$)/.test(
      url.pathname,
    );
    return isApi && isCatalogue;
  },
  handler: new StaleWhileRevalidate({
    cacheName: 'tb-catalogue',
    plugins: [
      new ExpirationPlugin({ maxEntries: 200, maxAgeSeconds: 60 * 60 * 24 }),
      new CacheableResponsePlugin({ statuses: [0, 200] }),
    ],
  }),
};

const serwist = new Serwist({
  precacheEntries: self.__SW_MANIFEST,
  skipWaiting: true,
  clientsClaim: true,
  navigationPreload: true,
  // Catalogue rule first so it wins over the Next defaults for API calls;
  // everything else (Next assets, pages) uses Serwist's sensible defaults.
  runtimeCaching: [catalogueCaching, ...defaultCache],
  // Offline fallback for navigations that can't be served.
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
