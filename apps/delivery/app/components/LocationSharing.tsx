'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { AuthRequiredError, shareLocation, stopSharingLocation } from '@/app/lib/api';
import { useAuth } from './AuthProvider';

/** At most one report this often; the customer's page polls every ~6 s anyway. */
const REPORT_EVERY_MS = 8_000;

/**
 * The rider switched sharing OFF themselves. Remembered per phone so it does
 * not quietly come back on at the next load — turning it off is a choice about
 * their own whereabouts, and it should stick until they turn it back on.
 */
const OPT_OUT_KEY = 'tb.delivery.location.off.v1';

type State =
  | 'idle' // nothing to share (no deliveries), or not started yet
  | 'sharing' // watchPosition running, reports going out
  | 'off' // rider switched it off
  | 'denied' // browser permission refused
  | 'unsupported'; // no Geolocation API at all

function hasOptedOut(): boolean {
  try {
    return window.localStorage.getItem(OPT_OUT_KEY) === '1';
  } catch {
    return false;
  }
}

function setOptedOut(off: boolean): void {
  try {
    if (off) window.localStorage.setItem(OPT_OUT_KEY, '1');
    else window.localStorage.removeItem(OPT_OUT_KEY);
  } catch {
    /* private mode — the choice just doesn't survive a reload */
  }
}

/**
 * Shares the rider's live position with the customers whose orders they are
 * carrying, and shows them that it is happening.
 *
 * <p>Runs only while {@code active} — while the queue has deliveries in it.
 * With nothing to deliver there is nobody to share with, so the watch stops
 * and the server is told to forget the last fix; the rider's whereabouts off
 * the job are nobody's business, and the app makes that true by construction
 * rather than by policy.
 *
 * <p>Starts on its own when there is work, because the rider is on a bike and
 * should not have to remember a switch — but is honest about it (the pill is
 * always visible while sharing) and has an off switch that is remembered.
 *
 * <p>Reports are throttled to one every {@link REPORT_EVERY_MS}: the phone can
 * fire fixes several times a second, the customer's page only re-reads every
 * few seconds, and every extra request is battery and data on a rider's phone.
 *
 * <p>Known limit: on iOS Safari the Geolocation watch pauses when the screen
 * locks or the app goes to the background, and the server hides a fix older
 * than three minutes. So a rider whose phone is in a pocket shows on the
 * customer's map only while it was last unlocked. That is a platform
 * constraint on web apps, not a bug here, and the customer's page degrades to
 * "no position" rather than showing a stale one.
 */
export default function LocationSharing({ active }: { active: boolean }) {
  const { refresh } = useAuth();
  const [state, setState] = useState<State>('idle');
  const watchIdRef = useRef<number | null>(null);
  const lastSentRef = useRef(0);
  // Whether the server may currently hold a fix of ours — i.e. whether a
  // "forget it" is owed when sharing ends. Separate from watchIdRef on
  // purpose: React runs an effect's cleanup BEFORE the next effect body, so by
  // the time the queue-emptied branch below runs, clearWatch has already
  // nulled watchIdRef and it can no longer tell us whether we were sharing.
  // Only the intentional stops (queue empty, Stop) clear this.
  const sharingRef = useRef(false);

  const stopWatching = useCallback(() => {
    if (watchIdRef.current !== null && typeof navigator !== 'undefined' && navigator.geolocation) {
      navigator.geolocation.clearWatch(watchIdRef.current);
    }
    watchIdRef.current = null;
  }, []);

  const startWatching = useCallback(() => {
    if (typeof navigator === 'undefined' || !navigator.geolocation) {
      setState('unsupported');
      return;
    }
    if (watchIdRef.current !== null) return;
    setState('sharing');
    sharingRef.current = true;
    watchIdRef.current = navigator.geolocation.watchPosition(
      (pos) => {
        const now = Date.now();
        if (now - lastSentRef.current < REPORT_EVERY_MS) return;
        lastSentRef.current = now;
        const { latitude, longitude, accuracy } = pos.coords;
        shareLocation(latitude, longitude, Number.isFinite(accuracy) ? accuracy : null).catch(
          (err) => {
            if (err instanceof AuthRequiredError) {
              refresh();
              return;
            }
            // Dead zone or a hiccup: the next fix will try again. Nothing to
            // show the rider — they can see their own signal bars.
          },
        );
      },
      (err) => {
        if (err.code === err.PERMISSION_DENIED) {
          stopWatching();
          setState('denied');
        }
        // POSITION_UNAVAILABLE / TIMEOUT are transient in a moving vehicle;
        // the watch keeps going and the customer sees the last fresh fix.
      },
      { enableHighAccuracy: true, maximumAge: 5_000, timeout: 20_000 },
    );
  }, [refresh, stopWatching]);

  // Start and stop with the queue.
  useEffect(() => {
    if (!active) {
      stopWatching();
      setState('idle');
      // Queue emptied: nothing more to share, so forget the last fix too.
      if (sharingRef.current) {
        sharingRef.current = false;
        stopSharingLocation().catch(() => undefined);
      }
      return;
    }
    if (hasOptedOut()) {
      setState('off');
      return;
    }
    startWatching();
    return () => {
      // Unmount (sign-out, navigation): stop the watch. DeliveryQueue clears
      // the server-side fix on sign-out itself, while it still has a token.
      stopWatching();
    };
  }, [active, startWatching, stopWatching]);

  const turnOff = () => {
    setOptedOut(true);
    stopWatching();
    sharingRef.current = false;
    setState('off');
    stopSharingLocation().catch(() => undefined);
  };

  const turnOn = () => {
    setOptedOut(false);
    lastSentRef.current = 0;
    startWatching();
  };

  if (!active || state === 'idle' || state === 'unsupported') return null;

  if (state === 'denied') {
    return (
      <p className="location-banner" role="status">
        <span aria-hidden>📍</span> Location is blocked for this app, so customers can&apos;t
        see you on the way. Allow location in your browser settings, then reload.
      </p>
    );
  }

  return (
    <div className={`location-pill ${state === 'sharing' ? 'on' : 'off'}`} role="status">
      <span className="location-dot" aria-hidden />
      {state === 'sharing' ? (
        <>
          <span>Sharing your location with customers</span>
          <button type="button" className="location-pill-btn" onClick={turnOff}>
            Stop
          </button>
        </>
      ) : (
        <>
          <span>Location off — customers can&apos;t see you on the way</span>
          <button type="button" className="location-pill-btn" onClick={turnOn}>
            Turn on
          </button>
        </>
      )}
    </div>
  );
}
