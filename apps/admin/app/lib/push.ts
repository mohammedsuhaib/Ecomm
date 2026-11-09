// Web Push for the staff dashboard, so a new order reaches a tablet whose screen
// is off or whose app is closed — the case a page-scoped chime cannot cover.
//
// Everything here is best-effort and silent. The on-page chime and the order
// poll keep working whether or not push is configured, permitted or supported,
// so a failure in this file must never surface to staff as an error.

import {
  getPushConfig,
  subscribeStaffToPush,
  unsubscribeFromPush,
  type PushSubscriptionPayload,
} from './api';

/** Whether this browser can do Web Push at all. */
export function pushSupported(): boolean {
  return (
    typeof window !== 'undefined' &&
    'serviceWorker' in navigator &&
    'PushManager' in window &&
    'Notification' in window
  );
}

/** VAPID keys travel as base64url; PushManager wants raw bytes. */
function urlBase64ToUint8Array(base64Url: string): Uint8Array {
  const padding = '='.repeat((4 - (base64Url.length % 4)) % 4);
  const base64 = (base64Url + padding).replace(/-/g, '+').replace(/_/g, '/');
  const raw = window.atob(base64);
  const output = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i++) output[i] = raw.charCodeAt(i);
  return output;
}

/**
 * Subscribe this browser for staff alerts and store it against the signed-in
 * account. Assumes notification permission is already granted; never prompts.
 *
 * <p>Idempotent: a browser hands back the same subscription until it rotates,
 * and the API updates the row in place, so this is also how a session that has
 * since been re-signed-in (or a different staffer on the same tablet) takes the
 * device over.
 *
 * <p>Uses {@code getRegistration()} rather than {@code serviceWorker.ready},
 * which never settles when no worker is registered (the dev build) and would
 * leave this hanging.
 */
export async function enableStaffPush(): Promise<boolean> {
  try {
    if (!pushSupported() || Notification.permission !== 'granted') return false;
    const config = await getPushConfig();
    if (!config.enabled || !config.publicKey) return false;
    const registration = await navigator.serviceWorker.getRegistration();
    if (!registration) return false;
    const subscription =
      (await registration.pushManager.getSubscription()) ??
      (await registration.pushManager.subscribe({
        // Chrome requires every push to be user-visible.
        userVisibleOnly: true,
        applicationServerKey: urlBase64ToUint8Array(config.publicKey),
      }));
    await subscribeStaffToPush(
      subscription.toJSON() as unknown as PushSubscriptionPayload,
    );
    return true;
  } catch {
    return false;
  }
}

/** Stop staff alerts on this browser, server-side row included. */
export async function disableStaffPush(): Promise<void> {
  try {
    if (!pushSupported()) return;
    const registration = await navigator.serviceWorker.getRegistration();
    const subscription = await registration?.pushManager.getSubscription();
    if (!subscription) return;
    // Server first: unsubscribing locally while the row survives would leave
    // the API pushing to a dead endpoint.
    await unsubscribeFromPush(subscription.endpoint).catch(() => undefined);
    await subscription.unsubscribe();
  } catch {
    /* best-effort */
  }
}
