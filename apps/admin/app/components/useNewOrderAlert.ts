'use client';

import { useCallback, useEffect, useRef, useState } from 'react';

const STORAGE_KEY = 'tb-admin-order-alert';

/**
 * Alerts staff to order activity while the dashboard is open — a new order, a
 * cancellation or a delivery — with a short chime plus a desktop notification,
 * so the queue does not have to be watched continuously.
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
/**
 * Show a desktop/phone notification.
 *
 * <p>Through the service worker when there is one, because `new Notification()`
 * is not allowed on Android Chrome or in an installed app there (it throws
 * "Illegal constructor"), which is where a phone on the counter would run this.
 * The constructor is the fallback for a browser with no worker registered, such
 * as a dev build.
 */
async function showNotification(title: string, options: NotificationOptions) {
  if (!('Notification' in window) || Notification.permission !== 'granted') return;
  try {
    const registration =
      'serviceWorker' in navigator
        ? await navigator.serviceWorker.getRegistration()
        : undefined;
    if (registration) {
      await registration.showNotification(title, options);
      return;
    }
  } catch {
    /* fall through to the constructor */
  }
  try {
    new Notification(title, options);
  } catch {
    // Unsupported or blocked: the chime already fired.
  }
}

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

  // The chime needs an AudioContext that a user gesture has unlocked, and that
  // only happened when someone pressed the Alerts button. After a reload, a
  // restart of the installed app or a deploy, the button reads "Alerts on" from
  // the remembered choice while no gesture has happened since, so the chime was
  // silently skipped. Any tap or key press on the dashboard is a gesture too:
  // use the first one to unlock audio again.
  useEffect(() => {
    if (!enabled) return;
    const unlock = () => {
      try {
        const Ctor =
          window.AudioContext ??
          (window as unknown as { webkitAudioContext?: typeof AudioContext })
            .webkitAudioContext;
        if (!Ctor) return;
        audioRef.current ??= new Ctor();
        if (audioRef.current.state === 'suspended') {
          void audioRef.current.resume();
        }
      } catch {
        /* no audio: the notification still shows */
      }
    };
    window.addEventListener('pointerdown', unlock, { once: true });
    window.addEventListener('keydown', unlock, { once: true });
    return () => {
      window.removeEventListener('pointerdown', unlock);
      window.removeEventListener('keydown', unlock);
    };
  }, [enabled]);

  const chime = useCallback(() => {
    const ctx = audioRef.current;
    if (!ctx) return;
    try {
      // A context the browser re-suspended (tab long in the background) wakes
      // up again if a gesture has happened since.
      if (ctx.state === 'suspended') void ctx.resume();
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

  /**
   * Call when an order event worth looking up for arrives. No-op while the
   * alert is switched off.
   *
   * <p>The caller supplies the title and tag because the queue alerts on more
   * than arrivals now — a cancellation and a delivery each need their own
   * heading, and their own tag so one does not silently replace the other. A
   * single hard-coded 'New order' title would have labelled a cancellation as
   * a new order, which is worse than not alerting at all.
   */
  const notify = useCallback(
    ({ title, body, tag }: { title: string; body: string; tag: string }) => {
      if (!enabled) return;
      chime();
      void showNotification(title, {
        body,
        // One notification per KIND — replace rather than stack a row per
        // order during a busy spell, without a delivery evicting the alert
        // about a brand-new order that still needs picking.
        tag,
        // A replaced notification is silent by default, so a second new order
        // while the first is still on screen would never buzz again.
        renotify: true,
      } as NotificationOptions);
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
