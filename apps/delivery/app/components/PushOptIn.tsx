'use client';

import { useCallback, useEffect, useState } from 'react';
import {
  getPushConfig,
  subscribeToPush,
  unsubscribeFromPush,
  type PushSubscriptionPayload,
} from '@/app/lib/api';

// VAPID keys travel as base64url; PushManager wants raw bytes.
function urlBase64ToUint8Array(base64Url: string): Uint8Array {
  const padding = '='.repeat((4 - (base64Url.length % 4)) % 4);
  const base64 = (base64Url + padding).replace(/-/g, '+').replace(/_/g, '/');
  const raw = window.atob(base64);
  const output = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i++) output[i] = raw.charCodeAt(i);
  return output;
}

function pushSupported(): boolean {
  return (
    typeof window !== 'undefined' &&
    'serviceWorker' in navigator &&
    'PushManager' in window &&
    'Notification' in window
  );
}

/**
 * Lets a rider turn on alerts for newly assigned deliveries — the one thing
 * they need to know while out on the road and not looking at the screen.
 *
 * Also owns the service-worker registration: push is the only reason this app
 * has a worker at all, so registering it here keeps the two together. It is
 * registered on mount (not on opt-in) so `serviceWorker.ready` has resolved by
 * the time the rider taps.
 *
 * Prompted only on an explicit tap: a permission dialog a rider didn't ask for
 * gets dismissed, and a dismissed prompt is hard to recover from.
 */
export default function PushOptIn() {
  const [available, setAvailable] = useState(false);
  const [publicKey, setPublicKey] = useState<string | null>(null);
  const [subscribed, setSubscribed] = useState(false);
  const [busy, setBusy] = useState(false);
  const [denied, setDenied] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    if (!pushSupported()) return;

    (async () => {
      try {
        const config = await getPushConfig();
        if (cancelled || !config.enabled || !config.publicKey) return;
        const registration = await navigator.serviceWorker.register('/sw.js');
        await navigator.serviceWorker.ready;
        const existing = await registration.pushManager.getSubscription();
        if (cancelled) return;
        setPublicKey(config.publicKey);
        setAvailable(true);
        setSubscribed(existing !== null);
        setDenied(Notification.permission === 'denied');
      } catch {
        // Alerts are a helper, not the job — a failed probe just hides them.
      }
    })();

    return () => {
      cancelled = true;
    };
  }, []);

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
      const registration = await navigator.serviceWorker.ready;
      const subscription =
        (await registration.pushManager.getSubscription()) ??
        (await registration.pushManager.subscribe({
          userVisibleOnly: true,
          applicationServerKey: urlBase64ToUint8Array(publicKey),
        }));
      await subscribeToPush(
        subscription.toJSON() as unknown as PushSubscriptionPayload,
      );
      setSubscribed(true);
    } catch {
      setError('Could not turn on alerts. Please try again.');
    } finally {
      setBusy(false);
    }
  }, [publicKey]);

  const disable = useCallback(async () => {
    setBusy(true);
    setError(null);
    try {
      const registration = await navigator.serviceWorker.ready;
      const subscription = await registration.pushManager.getSubscription();
      if (subscription) {
        // Server first: dropping it locally while the row survives would leave
        // the API pushing at a dead endpoint.
        await unsubscribeFromPush(subscription.endpoint).catch(() => undefined);
        await subscription.unsubscribe();
      }
      setSubscribed(false);
    } catch {
      setError('Could not turn off alerts. Please try again.');
    } finally {
      setBusy(false);
    }
  }, []);

  if (!available) return null;

  if (denied && !subscribed) {
    return (
      <p className="push-hint">
        Alerts are blocked for this app. Allow notifications in your browser
        settings to hear about new deliveries.
      </p>
    );
  }

  return (
    <div className="push-row">
      <button
        type="button"
        className={`push-toggle ${subscribed ? 'on' : ''}`}
        onClick={subscribed ? disable : enable}
        disabled={busy}
        aria-pressed={subscribed}
      >
        {busy
          ? 'Just a moment…'
          : subscribed
            ? '🔔 Alerts on'
            : '🔕 Turn on new-delivery alerts'}
      </button>
      {error && (
        <p className="push-hint error" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
