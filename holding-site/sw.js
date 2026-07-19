/* Town Basket holding site — minimal service worker.
 *
 * Strategy: network-first with cache fallback for every same-origin GET.
 * - Online: always fetch fresh (policy pages must never go stale — Razorpay
 *   KYC and customers read them), and refresh the cache in passing.
 * - Offline: serve the last cached copy, so the installed app still opens
 *   with the pages you've visited.
 *
 * Bump CACHE_VERSION on any breaking change; old caches are dropped on
 * activate.
 */
const CACHE_VERSION = 'tb-holding-v2';

const PRECACHE = [
  '/',
  '/index.html',
  '/terms.html',
  '/privacy.html',
  '/refund.html',
  '/shipping.html',
  '/contact.html',
  '/styles.css',
  '/sw-register.js',
  '/install.js',
  '/manifest.webmanifest',
  '/icons/icon-192.png',
  '/icons/icon-512.png',
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches
      .open(CACHE_VERSION)
      .then((cache) => cache.addAll(PRECACHE))
      .then(() => self.skipWaiting()),
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) =>
        Promise.all(keys.filter((k) => k !== CACHE_VERSION).map((k) => caches.delete(k))),
      )
      .then(() => self.clients.claim()),
  );
});

self.addEventListener('fetch', (event) => {
  const { request } = event;
  if (request.method !== 'GET' || new URL(request.url).origin !== self.location.origin) {
    return; // let the browser handle it
  }
  event.respondWith(
    fetch(request)
      .then((response) => {
        // Refresh the cache with the fresh copy (clone before the body is used).
        const copy = response.clone();
        caches.open(CACHE_VERSION).then((cache) => cache.put(request, copy));
        return response;
      })
      .catch(() =>
        caches.match(request).then(
          (cached) =>
            cached ??
            // Offline navigation to an uncached URL: fall back to the home page.
            (request.mode === 'navigate' ? caches.match('/index.html') : Response.error()),
        ),
      ),
  );
});
