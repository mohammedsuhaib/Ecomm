// "Add to Home Screen" mechanics for the admin dashboard PWA.
//
// Installing matters here for a different reason than on the storefront. This
// screen is open all shift on a shop laptop or counter tablet, and an installed
// launch drops the tab strip, the address bar and the bookmarks row — three
// bands of chrome across a display that is showing a live order queue. It also
// gives staff a taskbar or home-screen icon to reopen after the machine
// restarts, instead of hunting for a bookmark.
//
// Two browsers, two completely different mechanisms:
//
//   * Chrome/Edge (the shop's laptop) fire `beforeinstallprompt` once the site
//     passes their installability checks. Calling preventDefault() on it
//     suppresses the browser's own mini-infobar and hands us the event to
//     replay from our own button.
//   * iOS Safari (a counter iPad) fires nothing and exposes no API. Install is
//     a manual trip through the Share sheet, so all we can do is show the steps.
//
// This is a deliberate near-copy of the storefront's app/lib/install.ts rather
// than a shared package: the two apps already keep their own hand-written API
// clients for the same reason, and the workspace has no shared runtime package
// to put it in. Keep them in step when fixing a browser quirk in either.

/**
 * The Chromium-only event that carries the deferred install prompt.
 *
 * Not in TypeScript's DOM library — it is not a standard, and Safari and
 * Firefox never fire it — so the shape is declared here.
 */
export interface BeforeInstallPromptEvent extends Event {
  readonly platforms: readonly string[];
  readonly userChoice: Promise<{
    outcome: 'accepted' | 'dismissed';
    platform: string;
  }>;
  prompt(): Promise<void>;
}

/** Outcome of {@link promptInstall}. */
export type InstallOutcome = 'accepted' | 'dismissed' | 'unavailable';

/**
 * The staffer said no. Permanent: a bar that reappears every shift on the
 * screen someone works in all day is worse than never asking.
 */
const DISMISSED_KEY = 'tb.admin.install.dismissed.v1';

// ---- Deferred prompt capture ---------------------------------------------
//
// The listener is registered at MODULE scope, not from a component effect,
// because `beforeinstallprompt` fires once and is not replayed. An event that
// landed before the effect ran would be lost for the whole session, with no way
// to ever show the button. Evaluating this module is the earliest moment any
// client code in the app can listen.

let deferredPrompt: BeforeInstallPromptEvent | null = null;
let installed = false;

const listeners = new Set<() => void>();

function notify(): void {
  for (const listener of listeners) listener();
}

if (typeof window !== 'undefined') {
  window.addEventListener('beforeinstallprompt', (event) => {
    // Suppress the browser's own mini-infobar so staff get one ask, in our
    // wording, rather than two competing ones.
    event.preventDefault();
    deferredPrompt = event as BeforeInstallPromptEvent;
    notify();
  });

  // Fired after an install completes by any route — our button, the browser
  // menu, or the address-bar icon. Drop the bar immediately; a prompt to
  // install an app you just installed reads as broken.
  window.addEventListener('appinstalled', () => {
    deferredPrompt = null;
    installed = true;
    notify();
  });
}

/** Subscribe to changes in installability. Returns an unsubscribe function. */
export function subscribeToInstallState(listener: () => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

/** Whether a deferred Chromium prompt is in hand and ready to replay. */
export function canPromptInstall(): boolean {
  return deferredPrompt !== null;
}

/**
 * Whether the dashboard is already running installed.
 *
 * <p>`display-mode: standalone` covers desktop and Android; iOS Safari predates
 * that media query for home-screen apps and reports `navigator.standalone`
 * instead, so both are checked.
 */
export function isInstalled(): boolean {
  if (typeof window === 'undefined') return false;
  if (installed) return true;
  try {
    if (window.matchMedia('(display-mode: standalone)').matches) return true;
  } catch {
    /* matchMedia is universally supported; be safe anyway */
  }
  return (navigator as Navigator & { standalone?: boolean }).standalone === true;
}

/**
 * Whether this is iOS Safari, where install exists but only by hand.
 *
 * <p>Deliberately narrow. Every iOS browser is WebKit underneath, but only
 * Safari has "Add to Home Screen" — Chrome, Firefox and Edge on iOS cannot
 * install at all, so showing them the Share-sheet steps would send a staffer
 * hunting for a button that isn't there.
 */
export function isIosSafari(): boolean {
  if (typeof navigator === 'undefined') return false;
  const ua = navigator.userAgent;

  // iPadOS 13+ reports itself as "Macintosh"; the touch points give it away —
  // and a counter iPad is exactly the device this branch exists for.
  const iPadOs =
    navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1;
  if (!/iphone|ipad|ipod/i.test(ua) && !iPadOs) return false;

  // Exclude the other iOS browsers, which are WebKit but cannot install.
  return !/crios|fxios|edgios|opios|mercury/i.test(ua);
}

/**
 * Replay the deferred Chromium prompt.
 *
 * <p>Must be called from a user gesture — Chrome rejects a prompt that isn't.
 * The event is single-use: once shown it cannot be shown again, so it is
 * cleared either way and the bar does not come back this session.
 */
export async function promptInstall(): Promise<InstallOutcome> {
  const event = deferredPrompt;
  if (!event) return 'unavailable';

  // Cleared before awaiting: a double-click would otherwise call prompt() twice
  // on the same event, which throws.
  deferredPrompt = null;
  notify();

  try {
    await event.prompt();
    const { outcome } = await event.userChoice;
    return outcome;
  } catch {
    return 'unavailable';
  }
}

// ---- Dismissal ------------------------------------------------------------

/** Whether this browser has already declined. */
export function isInstallDismissed(): boolean {
  try {
    return window.localStorage.getItem(DISMISSED_KEY) === '1';
  } catch {
    // No storage means no memory of a dismissal, and a bar we cannot remember
    // dismissing would return on every load. Treat it as dismissed.
    return true;
  }
}

/** Remember that the staffer declined; the bar does not come back. */
export function rememberInstallDismissed(): void {
  try {
    window.localStorage.setItem(DISMISSED_KEY, '1');
  } catch {
    /* private mode — isInstallDismissed() already errs towards silence */
  }
}
