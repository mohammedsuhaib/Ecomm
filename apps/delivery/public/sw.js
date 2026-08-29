// Service worker for the delivery (rider) app.
//
// Push only — deliberately no offline caching. A rider must never be shown a
// stale order queue or a cached delivery OTP from a previous job, so every
// request goes to the network; the app surfaces offline state explicitly
// instead (see DeliveryQueue's online/offline banner).
//
// Plain JS on purpose: the storefront compiles its worker through Serwist for
// caching strategies, which this app has no use for.

const NOTIFICATION_TAG_PREFIX = 'tb-delivery-order-';

self.addEventListener('install', () => {
  // Take over immediately so a rider who just enabled notifications does not
  // have to close every tab before push starts working.
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(self.clients.claim());
});

// Payload is the JSON built by the API's notifications module:
// { title, body, url, orderId, type, status }.
//
// Every push MUST produce a visible notification — browsers revoke push
// permission from sites that receive pushes silently — so the catch-all below
// still shows something.
self.addEventListener('push', (event) => {
  let payload = {};
  try {
    payload = event.data ? event.data.json() : {};
  } catch (e) {
    // Non-JSON payload; fall through to the generic copy.
  }

  const title = payload.title || 'Town Basket Delivery';
  const isAssignment = payload.type === 'ORDER_ASSIGNED';

  event.waitUntil(
    self.registration.showNotification(title, {
      body: payload.body || 'You have a delivery update.',
      icon: '/icons/icon-192.png',
      badge: '/icons/icon-192.png',
      // One notification per order, so a reassignment replaces the earlier
      // one rather than leaving two contradictory cards on the lock screen.
      tag: payload.orderId
        ? NOTIFICATION_TAG_PREFIX + payload.orderId
        : 'tb-delivery',
      renotify: true,
      // A new job is worth a buzz in the rider's pocket; a cancellation or
      // hand-away is informational and should not vibrate mid-ride.
      vibrate: isAssignment ? [200, 100, 200] : undefined,
      requireInteraction: isAssignment,
      data: { url: payload.url || '/' },
    }),
  );
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const target = (event.notification.data && event.notification.data.url) || '/';

  event.waitUntil(
    (async () => {
      const clients = await self.clients.matchAll({
        type: 'window',
        includeUncontrolled: true,
      });
      for (const client of clients) {
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
