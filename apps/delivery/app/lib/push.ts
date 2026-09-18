import { unsubscribeFromPush } from '@/app/lib/api';

/**
 * A rider who turned alerts off with the toggle.
 *
 * <p>Needed because the browser permission stays {@code granted} after an
 * unsubscribe — nothing in the Push API records that the person chose to stop.
 * Without this flag the re-arm on the next sign-in would helpfully re-subscribe
 * a rider who had just deliberately switched alerts off. Turning them back on
 * clears it.
 */
const OPT_OUT_KEY = 'tb.delivery.push.optedOut.v1';

export function rememberPushOptOut(): void {
  try {
    window.localStorage.setItem(OPT_OUT_KEY, '1');
  } catch {
    /* private mode — worst case the rider turns them off again */
  }
}

export function clearPushOptOut(): void {
  try {
    window.localStorage.removeItem(OPT_OUT_KEY);
  } catch {
    /* ignore */
  }
}

export function hasOptedOut(): boolean {
  try {
    return window.localStorage.getItem(OPT_OUT_KEY) === '1';
  } catch {
    return false;
  }
}

/**
 * Stop pushing to this phone, server-side row included.
 *
 * <p>Shared by the opt-in toggle and by sign-out. A subscription is stored
 * against the rider who registered it, so a phone that keeps its subscription
 * after sign-out keeps buzzing with that rider's jobs — on a handed-back or
 * shared phone, in someone else's pocket. Dropping it is part of signing out,
 * the same way the last GPS fix is.
 *
 * <p>Deliberately uses {@code getRegistration()} rather than
 * {@code serviceWorker.ready}: the worker is registered only by the opt-in
 * component, and only where push is supported and configured, so {@code ready}
 * can be a promise that never settles.
 */
export async function unsubscribeCurrentBrowser(): Promise<void> {
  if (typeof navigator === 'undefined' || !('serviceWorker' in navigator)) return;
  const registration = await navigator.serviceWorker.getRegistration();
  if (!registration?.pushManager) return;
  const subscription = await registration.pushManager.getSubscription();
  if (!subscription) return;
  // Server first: dropping it locally while the row survives would leave the
  // API pushing at an endpoint nobody can cancel any more. The DELETE is
  // authorised by the endpoint URL itself, so it still works with no token.
  await unsubscribeFromPush(subscription.endpoint).catch(() => undefined);
  await subscription.unsubscribe();
}
