'use client';

import { useTranslations } from 'next-intl';
import { useCallback, useEffect, useState } from 'react';
import { getPushConfig, unsubscribeFromPush } from '@/app/lib/api';
import { useAuth } from '@/app/components/AuthProvider';
import { pushSupported, subscribeCurrentBrowser } from '@/app/lib/push';

/**
 * Opt-in for browser notifications about this order ("your order is on the
 * way") that arrive even when the app is closed.
 *
 * Renders nothing at all unless it can actually work — the browser supports
 * push, the deployment has VAPID keys, and the customer is signed in (the
 * subscription is stored against their account). Permission is only requested
 * on an explicit tap, never on page load: a prompt the customer didn't ask for
 * is the fastest way to get permanently blocked.
 */
export default function PushOptIn() {
  const t = useTranslations('order');
  const { isAuthenticated } = useAuth();

  const [available, setAvailable] = useState(false);
  const [publicKey, setPublicKey] = useState<string | null>(null);
  const [subscribed, setSubscribed] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [denied, setDenied] = useState(false);

  // Decide once whether this UI can do anything, and reflect the browser's
  // existing subscription so the toggle shows the true current state.
  useEffect(() => {
    let cancelled = false;
    if (!pushSupported() || !isAuthenticated) return;

    (async () => {
      try {
        const config = await getPushConfig();
        if (cancelled || !config.enabled || !config.publicKey) return;
        const registration = await navigator.serviceWorker.ready;
        const existing = await registration.pushManager.getSubscription();
        if (cancelled) return;
        setPublicKey(config.publicKey);
        setAvailable(true);
        setSubscribed(existing !== null);
        setDenied(Notification.permission === 'denied');
      } catch {
        // Push is a nice-to-have; a failed probe just hides the control.
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [isAuthenticated]);

  const enable = useCallback(async () => {
    if (!publicKey) return;
    setBusy(true);
    setError(null);
    try {
      const permission = await Notification.requestPermission();
      if (permission !== 'granted') {
        setDenied(permission === 'denied');
        return;
      }
      await subscribeCurrentBrowser(publicKey);
      setSubscribed(true);
    } catch {
      setError(t('notifyFailed'));
    } finally {
      setBusy(false);
    }
  }, [publicKey, t]);

  const disable = useCallback(async () => {
    setBusy(true);
    setError(null);
    try {
      const registration = await navigator.serviceWorker.ready;
      const subscription = await registration.pushManager.getSubscription();
      if (subscription) {
        // Tell the server first: if unsubscribing locally succeeded but the
        // server kept the row, it would keep pushing to a dead endpoint.
        await unsubscribeFromPush(subscription.endpoint).catch(() => undefined);
        await subscription.unsubscribe();
      }
      setSubscribed(false);
    } catch {
      setError(t('notifyFailed'));
    } finally {
      setBusy(false);
    }
  }, [t]);

  if (!available) return null;

  return (
    <div className="push-optin">
      {denied && !subscribed ? (
        <p className="muted push-optin-hint">{t('notifyBlocked')}</p>
      ) : (
        <>
          <button
            type="button"
            className="btn btn-outline push-optin-btn"
            onClick={subscribed ? disable : enable}
            disabled={busy}
            aria-pressed={subscribed}
          >
            {busy
              ? t('notifyWorking')
              : subscribed
                ? t('notifyOn')
                : t('notifyEnable')}
          </button>
          <p className="muted push-optin-hint">
            {subscribed ? t('notifyOnHint') : t('notifyEnableHint')}
          </p>
        </>
      )}
      {error && (
        <p className="notice error" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
