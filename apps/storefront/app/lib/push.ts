// Web Push subscription mechanics, shared by the automatic opt-in offered
// alongside the location prompt and the explicit button on the order page.
//
// The two entry points exist because permission and subscription are separate
// steps with different requirements, and they rarely happen at the same moment:
//
//   * Asking for permission needs a user gesture. Safari refuses outright
//     without one, and Chrome can permanently suppress a site that prompts
//     unbidden, so this is only ever called from inside a tap handler.
//   * Storing the subscription needs a signed-in account — it is saved against
//     the user so their order updates can find them — but needs no gesture.
//
// A visitor usually grants location (and notifications) before they ever log
// in, so the grant is captured at the tap and the subscription is completed
// later, the moment there is an account to attach it to.

import {
  getPushConfig,
  subscribeToPush,
  type PushSubscriptionPayload,
} from '@/app/lib/api';

/** VAPID keys travel as base64url; PushManager wants raw bytes. */
export function urlBase64ToUint8Array(base64Url: string): Uint8Array {
  const padding = '='.repeat((4 - (base64Url.length % 4)) % 4);
  const base64 = (base64Url + padding).replace(/-/g, '+').replace(/_/g, '/');
  const raw = window.atob(base64);
  const output = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i++) output[i] = raw.charCodeAt(i);
  return output;
}

/** Whether this browser can do Web Push at all. */
export function pushSupported(): boolean {
  return (
    typeof window !== 'undefined' &&
    'serviceWorker' in navigator &&
    'PushManager' in window &&
    'Notification' in window
  );
}

/**
 * Subscribe this browser and store it against the signed-in account.
 *
 * Assumes permission is already granted — it never prompts, so it is safe to
 * call without a user gesture. Returns false when there is nothing to do.
 */
export async function subscribeCurrentBrowser(
  publicKey: string,
): Promise<boolean> {
  const registration = await navigator.serviceWorker.ready;
  const subscription =
    (await registration.pushManager.getSubscription()) ??
    (await registration.pushManager.subscribe({
      // Chrome requires every push to be user-visible.
      userVisibleOnly: true,
      applicationServerKey: urlBase64ToUint8Array(publicKey),
    }));
  await subscribeToPush(
    subscription.toJSON() as unknown as PushSubscriptionPayload,
  );
  return true;
}

/**
 * Ask for notification permission, piggy-backing on a tap the customer has
 * already made for something else (granting location). Does NOT subscribe:
 * at this point in the flow there is usually no account yet.
 *
 * Silent about everything. This is a bonus attached to an action the customer
 * took for another reason, so a browser that refuses, a deployment with no
 * VAPID keys, or a customer who says no must all leave the original action
 * looking exactly as it would have.
 *
 * Deliberately returns without prompting when permission is already decided:
 * re-asking a customer who said no is both futile (browsers keep the denial)
 * and the behaviour that gets a site's prompt permanently suppressed.
 */
export async function requestNotificationPermissionQuietly(): Promise<void> {
  try {
    if (!pushSupported() || Notification.permission !== 'default') return;
    // Only prompt where a notification could actually be delivered — asking on
    // a deployment with push switched off spends the customer's one good
    // impression on nothing.
    const config = await getPushConfig();
    if (!config.enabled || !config.publicKey) return;
    await Notification.requestPermission();
  } catch {
    /* never let this disturb whatever the customer was actually doing */
  }
}

/**
 * Complete the subscription for a customer who has already granted
 * permission — typically the one captured beside the location prompt before
 * they signed in.
 *
 * Call after login. No prompt, no gesture needed, and silent throughout: a
 * failure here must never interfere with signing in, and the explicit button
 * on the order page remains the way a customer turns this on deliberately.
 */
export async function subscribeIfAlreadyPermitted(): Promise<void> {
  try {
    if (!pushSupported() || Notification.permission !== 'granted') return;
    const config = await getPushConfig();
    if (!config.enabled || !config.publicKey) return;
    await subscribeCurrentBrowser(config.publicKey);
  } catch {
    /* best-effort — the order page's opt-in button is the deliberate path */
  }
}
