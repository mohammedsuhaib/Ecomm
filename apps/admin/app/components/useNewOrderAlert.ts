'use client';

import { useCallback, useEffect, useRef, useState } from 'react';

const STORAGE_KEY = 'tb-admin-order-alert';

/**
 * Alerts staff to a new order while the dashboard is open: a short chime plus a
 * desktop notification, so the queue does not have to be watched continuously.
 *
 * <p>Deliberately page-scoped rather than Web Push — staff keep the dashboard
 * open through the shift, and a desktop notification from an open tab needs no
 * service worker, no VAPID keys and no per-device subscription. It fires while
 * the tab is backgrounded, which is the case that actually matters.
 *
 * <p>The chime is synthesised with WebAudio so there is no audio asset to ship
 * or cache. Browsers block audio until the user interacts with the page, so the
 * AudioContext is created on the toggle click (a real user gesture) and reused.
 */
export function useNewOrderAlert() {
  // Off until switched on: a store laptop should not start making noise by
  // itself. The choice is remembered per browser.
  const [enabled, setEnabled] = useState(false);
  const audioRef = useRef<AudioContext | null>(null);

  useEffect(() => {
    try {
      setEnabled(window.localStorage.getItem(STORAGE_KEY) === 'on');
    } catch {
      // Private mode / storage blocked — stay off.
    }
  }, []);

  const chime = useCallback(() => {
    const ctx = audioRef.current;
    if (!ctx) return;
    try {
      // Two short rising notes — audible in a shop without being alarming.
      const now = ctx.currentTime;
      [
        { freq: 880, at: 0 },
        { freq: 1320, at: 0.18 },
      ].forEach(({ freq, at }) => {
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();
        osc.type = 'sine';
        osc.frequency.value = freq;
        // Fade each note in and out so it doesn't click.
        gain.gain.setValueAtTime(0.0001, now + at);
        gain.gain.exponentialRampToValueAtTime(0.25, now + at + 0.02);
        gain.gain.exponentialRampToValueAtTime(0.0001, now + at + 0.16);
        osc.connect(gain).connect(ctx.destination);
        osc.start(now + at);
        osc.stop(now + at + 0.18);
      });
    } catch {
      // Audio is a convenience; never let it break the queue.
    }
  }, []);

  /** Call when a new order arrives. No-op while the alert is switched off. */
  const notify = useCallback(
    (message: string) => {
      if (!enabled) return;
      chime();
      try {
        if ('Notification' in window && Notification.permission === 'granted') {
          new Notification('New order', {
            body: message,
            // One notification at a time — replace rather than stack a row per
            // order during a busy spell.
            tag: 'tb-new-order',
          });
        }
      } catch {
        // Notification unsupported or blocked — the chime already fired.
      }
    },
    [enabled, chime],
  );

  /**
   * Flip the alert on or off. Turning it on runs inside the click handler, so
   * it is a valid user gesture for both unlocking audio and asking for
   * notification permission.
   */
  const toggle = useCallback(async () => {
    if (enabled) {
      setEnabled(false);
      try {
        window.localStorage.setItem(STORAGE_KEY, 'off');
      } catch {
        /* ignore */
      }
      return;
    }

    try {
      const Ctor =
        window.AudioContext ??
        (window as unknown as { webkitAudioContext?: typeof AudioContext })
          .webkitAudioContext;
      if (Ctor) {
        audioRef.current ??= new Ctor();
        // Safari/Chrome start contexts suspended until a gesture resumes them.
        if (audioRef.current.state === 'suspended') {
          await audioRef.current.resume();
        }
      }
    } catch {
      // No audio — the desktop notification still works.
    }

    try {
      if ('Notification' in window && Notification.permission === 'default') {
        await Notification.requestPermission();
      }
    } catch {
      /* denied or unsupported — the chime still works */
    }

    setEnabled(true);
    try {
      window.localStorage.setItem(STORAGE_KEY, 'on');
    } catch {
      /* ignore */
    }
  }, [enabled]);

  return { enabled, toggle, notify };
}
