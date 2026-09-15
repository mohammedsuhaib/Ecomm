// Web Push subscription mechanics, shared by the automatic opt-in offered
// alongside the location prompt and the explicit button on the order page.
//
// Permission and subscription are separate steps with different requirements,
// and they rarely happen at the same moment:
//
//   * Asking for permission needs a user gesture. Safari refuses without one,
//     and Chrome's transient activation expires, so the prompt must be raised
//     synchronously from inside a tap handler — see
//     requestNotificationPermissionOnGesture, which is callable but not
//     awaitable for exactly that reason.
//   * Storing the subscription needs a signed-in account — it is saved against
//     the user so their order updates can find them — but needs no gesture.
//
// So the grant is taken whenever it can be, and redeemed whenever an account
// is available: immediately if the customer is already signed in, otherwise at
// their next login.

import {
  getPushConfig,
  subscribeToPush,
  unsubscribeFromPush,
  type PushConfig,
  type PushSubscriptionPayload,
} from '@/app/lib/api';

/**
 * The deployment's push config, fetched ahead of any tap.
 *
 * <p>This cache is the whole reason the permission prompt works. Asking the
 * server whether push is configured is a network round-trip, and awaiting one
 * before calling {@code Notification.requestPermission()} ends the user
 * gesture the browser requires — Safari then rejects the call outright and
 * Chrome does too once its activation window closes, so the prompt would never
 * appear and the failure would be silent. Fetching early and reading the
 * result synchronously at tap time is what keeps the call inside the gesture.
 *
 * <p>{@code undefined} means "not fetched yet", {@code null} means the fetch
 * failed.
 */
let cachedConfig: PushConfig | null | undefined;

/**
 * Start fetching the push config. Call on mount of anything that might later
 * raise the prompt — never from the tap handler itself.
 */
export function primePushConfig(): void {
  if (cachedConfig !== undefined) return;
  getPushConfig()
    .then((config) => {
      cachedConfig = config;
    })
    .catch(() => {
      cachedConfig = null;
    });
}

/**
 * A customer who turned notifications off with the order page's button.
 *
 * <p>Needed because the browser permission stays {@code granted} after an
 * unsubscribe — nothing in the Push API records that the person chose to stop.
 * Without this flag the next sign-in would helpfully re-subscribe someone who
 * had just deliberately opted out, which is the opposite of what they asked
 * for. Turning notifications back on clears it.
 */
const OPT_OUT_KEY = 'tb.push.optedOut.v1';

export function rememberPushOptOut(): void {
  try {
    window.localStorage.setItem(OPT_OUT_KEY, '1');
  } catch {
    /* private mode — worst case the customer opts out again */
  }
}

export function clearPushOptOut(): void {
  try {
    window.localStorage.removeItem(OPT_OUT_KEY);
  } catch {
    /* ignore */
  }
}

function hasOptedOut(): boolean {
  try {
    return window.localStorage.getItem(OPT_OUT_KEY) === '1';
  } catch {
    return false;
  }
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
 * call without a user gesture. Throws if there is no session to attach to.
 */
export async function subscribeCurrentBrowser(
  publicKey: string,
): Promise<void> {
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
  clearPushOptOut();
}

/** Stop notifications on this browser, server-side row included. */
export async function unsubscribeCurrentBrowser(): Promise<void> {
  const registration = await navigator.serviceWorker.ready;
  const subscription = await registration.pushManager.getSubscription();
  if (!subscription) return;
  // Tell the server first: unsubscribing locally while the row survives would
  // leave it pushing to a dead endpoint.
  await unsubscribeFromPush(subscription.endpoint).catch(() => undefined);
  await subscription.unsubscribe();
}

/**
 * Ask for notification permission, piggy-backing on a tap the customer has
 * already made for something else (granting location).
 *
 * <p><strong>Synchronous on purpose, and not awaitable.</strong> Everything
 * before {@code Notification.requestPermission()} is a plain memory read, so
 * the call happens inside the gesture that the browser requires. Introducing
 * an {@code await} above it — a config fetch, most temptingly — is what
 * silently breaks this.
 *
 * <p>Silent about everything: this is a bonus attached to an action taken for
 * another reason, so a browser that refuses, a deployment with no VAPID keys,
 * or a customer who says no must all leave the original action looking exactly
 * as it would have.
 *
 * <p>On a grant it also subscribes straight away, because the customer may
 * well already be signed in — the picker is used on checkout and the address
 * book, both behind a session. If they are not, that attempt fails quietly and
 * their next login redeems the grant instead.
 */
export function requestNotificationPermissionOnGesture(): void {
  if (!pushSupported() || Notification.permission !== 'default') return;
  if (hasOptedOut()) return;
  const config = cachedConfig;
  // Not primed yet, the fetch failed, or push is switched off for this
  // deployment: say nothing rather than spend the customer's one good
  // impression on a prompt that could never deliver anything.
  if (!config?.enabled || !config.publicKey) return;

  // No await above this line.
  Notification.requestPermission()
    .then((permission) => {
      if (permission === 'granted') void subscribeIfAlreadyPermitted();
    })
    .catch(() => {
      /* refused by the browser — nothing to do */
    });
}

/**
 * Complete the subscription for a customer who has already granted
 * permission — the one captured beside the location prompt.
 *
 * Call after login, and after a fresh grant. No prompt, no gesture needed, and
 * silent throughout: a failure here must never interfere with signing in.
 */
export async function subscribeIfAlreadyPermitted(): Promise<void> {
  try {
    if (!pushSupported() || Notification.permission !== 'granted') return;
    if (hasOptedOut()) return;
    const config = cachedConfig ?? (await getPushConfig());
    cachedConfig = config;
    if (!config.enabled || !config.publicKey) return;
    await subscribeCurrentBrowser(config.publicKey);
  } catch {
    /* best-effort — the order page's opt-in button is the deliberate path */
  }
}

/** VAPID keys travel as base64url; PushManager wants raw bytes. */
export function urlBase64ToUint8Array(base64Url: string): Uint8Array {
  const padding = '='.repeat((4 - (base64Url.length % 4)) % 4);
  const base64 = (base64Url + padding).replace(/-/g, '+').replace(/_/g, '/');
  const raw = window.atob(base64);
  const output = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i++) output[i] = raw.charCodeAt(i);
  return output;
}
