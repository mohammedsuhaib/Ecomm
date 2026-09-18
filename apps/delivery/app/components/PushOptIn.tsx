'use client';

import { useCallback, useEffect, useState } from 'react';
import {
  getPushConfig,
  subscribeToPush,
  type PushSubscriptionPayload,
} from '@/app/lib/api';
import {
  clearPushOptOut,
  hasOptedOut,
  rememberPushOptOut,
  unsubscribeCurrentBrowser,
} from '@/app/lib/push';

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
 * Subscribe this phone and store the subscription against the signed-in rider.
 * Never prompts — it assumes permission is already granted — so it is safe to
 * call without a user gesture.
 */
async function register(
  registration: ServiceWorkerRegistration,
  publicKey: string,
): Promise<void> {
  const subscription =
    (await registration.pushManager.getSubscription()) ??
    (await registration.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: urlBase64ToUint8Array(publicKey),
    }));
  await subscribeToPush(
    subscription.toJSON() as unknown as PushSubscriptionPayload,
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
 * gets dismissed, and a dismissed prompt is hard to recover from. Re-arming an
 * existing grant on mount is not a prompt and is done silently — see below.
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
        if (cancelled) return;
        setPublicKey(config.publicKey);
        setAvailable(true);
        setDenied(Notification.permission === 'denied');

        // Re-arm a grant the rider already gave, without prompting. This
        // component only mounts once a rider is signed in, and a subscription
        // is stored against whoever registered it — so signing out drops it
        // (see app/lib/push.ts). Without this re-arm a rider would have to
        // switch alerts back on by hand at the start of every shift, and a
        // phone passed between riders would keep pushing one rider's jobs at
        // the next, because the stored row would still be pointing at them.
        if (Notification.permission === 'granted' && !hasOptedOut()) {
          await register(registration, config.publicKey);
          if (cancelled) return;
          setSubscribed(true);
          return;
        }

        const existing = await registration.pushManager.getSubscription();
        if (cancelled) return;
        setSubscribed(existing !== null);
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
      await register(registration, publicKey);
      clearPushOptOut();
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
      // Same teardown sign-out runs (app/lib/push.ts), so the two can't drift.
      await unsubscribeCurrentBrowser();
      // Remember the choice: the browser permission stays granted, so without
      // this the re-arm on mount would switch alerts straight back on.
      rememberPushOptOut();
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
