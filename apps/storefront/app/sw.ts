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

// ---- Web Push -------------------------------------------------------------
// Order updates pushed by the API (notifications module). The payload is the
// JSON built server-side: { title, body, url, orderId, type, status }.
//
// A push event MUST result in a visible notification: browsers revoke the push
// permission from sites that receive pushes silently, so every branch below —
// including a malformed payload — shows something.

interface PushPayload {
  title?: string;
  body?: string;
  url?: string | null;
  orderId?: number;
  type?: string;
  status?: string;
}

self.addEventListener('push', (event: PushEvent) => {
  let payload: PushPayload = {};
  try {
    payload = event.data ? (event.data.json() as PushPayload) : {};
  } catch {
    // Non-JSON payload — fall through to the generic copy below.
  }

  const title = payload.title ?? 'Town Basket';
  const url = payload.url ?? '/account';

  event.waitUntil(
    self.registration.showNotification(title, {
      body: payload.body ?? 'Your order has an update.',
      icon: '/icons/icon-192.png',
      badge: '/icons/icon-192.png',
      // Collapse repeat updates for the same order into one notification
      // rather than stacking a row per status change.
      tag: payload.orderId ? `tb-order-${payload.orderId}` : 'tb-order',
      renotify: true,
      data: { url },
    }),
  );
});

self.addEventListener('notificationclick', (event: NotificationEvent) => {
  event.notification.close();
  const target = (event.notification.data?.url as string | undefined) ?? '/account';

  // Focus an already-open tab (and navigate it) instead of piling up new ones.
  event.waitUntil(
    (async () => {
      const clientList = await self.clients.matchAll({
        type: 'window',
        includeUncontrolled: true,
      });
      for (const client of clientList) {
        if ('focus' in client) {
          await client.focus();
          if ('navigate' in client) {
            await client.navigate(target).catch(() => undefined);
          }
          return;
        }
      }
      await self.clients.openWindow(target);
    })(),
  );
});
