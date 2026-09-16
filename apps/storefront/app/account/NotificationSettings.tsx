'use client';

import { useTranslations } from 'next-intl';
import { useCallback, useEffect, useState } from 'react';
import { getPushConfig } from '@/app/lib/api';
import {
  pushSupported,
  rememberPushOptOut,
  subscribeCurrentBrowser,
  unsubscribeCurrentBrowser,
} from '@/app/lib/push';

/**
 * Order notifications, switchable from the account page — the one place a
 * customer can always reach.
 *
 * <p><strong>Why this exists.</strong> The only notification control used to
 * live on an order's tracking page, and only while that order was still in
 * flight. So a customer with nothing on the way — or one who had simply opened
 * their account on a different device — had no way to turn notifications on or
 * off at all, and no way to see whether they were on. That reads as a broken
 * setting rather than a missing screen.
 *
 * <p><strong>Per device, and it says so.</strong> A Web Push subscription
 * belongs to one browser on one device: the phone and the laptop each hold
 * their own, and granting permission on one genuinely does nothing for the
 * other. That is how the standard works, not a bug, but silence about it makes
 * "I turned it on in Chrome and my phone still says off" look like one. The
 * copy states it, and the toggle always reports THIS device.
 *
 * <p><strong>Never renders nothing.</strong> The order-page control hides
 * itself whenever push cannot work — browser unsupported, deployment without
 * VAPID keys, permission already denied — which is indistinguishable from a
 * bug. Each of those states is spelled out here instead, so a customer (and
 * whoever they report it to) can tell "off" from "unavailable".
 */
/**
 * How long to wait for a service worker before concluding there isn't one.
 * Generous enough for a first visit that is still installing one, short enough
 * that nobody is left staring at a skeleton.
 */
const SW_READY_TIMEOUT_MS = 5000;

/**
 * The active service worker, or {@code null} if this page hasn't got one.
 *
 * <p>{@code navigator.serviceWorker.ready} never rejects and never resolves
 * when no worker is or becomes registered — it simply waits forever. The
 * storefront's worker is disabled in development and absent from any build
 * where Serwist didn't emit it, so awaiting it directly left this component
 * stuck on 'loading' and rendering a permanent skeleton: exactly the "looks
 * broken" state the rest of the file exists to avoid. A timeout turns that
 * into an answer.
 */
async function activeServiceWorker(): Promise<ServiceWorkerRegistration | null> {
  let timer: ReturnType<typeof setTimeout> | undefined;
  try {
    return await Promise.race([
      navigator.serviceWorker.ready,
      new Promise<null>((resolve) => {
        timer = setTimeout(() => resolve(null), SW_READY_TIMEOUT_MS);
      }),
    ]);
  } finally {
    if (timer) clearTimeout(timer);
  }
}

export default function NotificationSettings() {
  const t = useTranslations('notifications');

  type State =
    | 'loading'
    | 'unsupported' // this browser has no Push API (or is an uninstalled iOS PWA)
    | 'unavailable' // the deployment has no VAPID keys — nothing can be sent
    | 'blocked' // permission denied in browser settings; only the user can undo it
    | 'on'
    | 'off';

  const [state, setState] = useState<State>('loading');
  const [publicKey, setPublicKey] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const probe = useCallback(async () => {
    if (!pushSupported()) {
      setState('unsupported');
      return;
    }
    try {
      const config = await getPushConfig();
      if (!config.enabled || !config.publicKey) {
        setState('unavailable');
        return;
      }
      setPublicKey(config.publicKey);
      // Permission is checked before the subscription: a denied site can hold a
      // stale subscription object, and offering a toggle that cannot possibly
      // work is the thing this component exists to avoid.
      if (Notification.permission === 'denied') {
        setState('blocked');
        return;
      }
      const registration = await activeServiceWorker();
      if (!registration) {
        // No worker means nothing can receive a push, so this is the same
        // "can't tell you it's on, can't offer to switch it on" state as a
        // deployment without keys — and it says so rather than spinning.
        setState('unavailable');
        return;
      }
      const existing = await registration.pushManager.getSubscription();
      setState(existing ? 'on' : 'off');
    } catch {
      // A failed probe is not the same as "off" — say we could not tell.
      setState('unavailable');
    }
  }, []);

  useEffect(() => {
    void probe();
  }, [probe]);

  const turnOn = useCallback(async () => {
    if (!publicKey) return;
    setBusy(true);
    setError(null);
    try {
      const permission = await Notification.requestPermission();
      if (permission !== 'granted') {
        setState(permission === 'denied' ? 'blocked' : 'off');
        return;
      }
      await subscribeCurrentBrowser(publicKey);
      setState('on');
    } catch {
      setError(t('failed'));
    } finally {
      setBusy(false);
    }
  }, [publicKey, t]);

  const turnOff = useCallback(async () => {
    setBusy(true);
    setError(null);
    try {
      await unsubscribeCurrentBrowser();
      // The browser permission survives an unsubscribe, so without this flag
      // the next sign-in would helpfully switch notifications back on for
      // someone who just turned them off.
      rememberPushOptOut();
      setState('off');
    } catch {
      setError(t('failed'));
    } finally {
      setBusy(false);
    }
  }, [t]);

  // Laid out like the rest of the account page: a bordered card with the
  // copy on the left and the control on the right of it, everything
  // left-aligned. It deliberately does NOT reuse the order page's
  // `.push-optin-hint`, which is centred and width-capped for a narrow column
  // and reads as misaligned in a stack of left-aligned cards.
  return (
    <section className="account-section">
      <div className="account-section-head">
        <h2 className="section-title" style={{ margin: 0 }}>
          {t('title')}
        </h2>
      </div>

      <div className="profile-card">
        {state === 'loading' ? (
          <div className="skeleton-row" aria-busy="true" aria-label={t('title')} />
        ) : state === 'on' || state === 'off' ? (
          <div className="notify-row">
            <div className="notify-copy">
              <p className="notify-lead">
                {state === 'on' ? t('onHint') : t('enableHint')}
              </p>
              <p className="notify-sub muted">{t('perDevice')}</p>
            </div>
            <button
              type="button"
              className="btn btn-outline notify-toggle"
              onClick={state === 'on' ? turnOff : turnOn}
              disabled={busy}
              aria-pressed={state === 'on'}
            >
              {busy ? t('working') : state === 'on' ? t('on') : t('enable')}
            </button>
          </div>
        ) : (
          // unsupported / unavailable / blocked: no control to offer, so the
          // card carries only the reason — which is the point of these states.
          <p className="notify-state muted">
            {state === 'unsupported'
              ? t('unsupported')
              : state === 'blocked'
                ? t('blocked')
                : t('unavailable')}
          </p>
        )}

        {error && <p className="field-error notify-error">{error}</p>}
      </div>
    </section>
  );
}
