// "Add to Home Screen" mechanics for the storefront PWA (ARCHITECTURE.md §4.1).
//
// Installing matters more here than on a typical site: an installed Town Basket
// launches standalone, keeps its service-worker caches warm between visits, and
// — on iOS — is the ONLY way the customer can receive order push notifications
// at all, because Safari refuses the Push API to a tab.
//
// Two browsers, two completely different mechanisms:
//
//   * Chrome/Edge/Samsung (Android, desktop) fire `beforeinstallprompt` once
//     the site passes their own installability and engagement checks. Calling
//     preventDefault() on it suppresses the browser's mini-infobar and hands us
//     the event to replay later from our own button.
//   * iOS Safari fires nothing and exposes no API. Install is a manual journey
//     through the Share sheet, so all we can do is show the steps.
//
// Everything below is browser-only; each entry point is guarded so the module
// stays importable from a server component.

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
 * The customer said no. Permanent, like the push opt-out: an install banner
 * that comes back after being dismissed is the kind of nagging that gets a
 * grocery app closed for good.
 */
const DISMISSED_KEY = 'tb.install.dismissed.v1';

/** How many separate visits this browser has made (see {@link countVisit}). */
const VISITS_KEY = 'tb.install.visits.v1';

/** Marks the current tab session as already counted. */
const VISIT_COUNTED_KEY = 'tb.install.counted';

/**
 * Visits before the banner may appear.
 *
 * <p>A first-time visitor is already meeting the location gate and an unfamiliar
 * shop; asking them to install on top of that converts badly and costs us the
 * one dismissal we get. Waiting for a second visit means we only ask people who
 * came back.
 *
 * <p>Chrome applies its own engagement heuristic before it will even fire
 * `beforeinstallprompt`, so there this is a second filter rather than the only
 * one. On iOS Safari, where no such heuristic exists, it is the only one.
 */
export const MIN_VISITS_BEFORE_PROMPT = 2;

// ---- Deferred prompt capture ---------------------------------------------
//
// The listener is registered at MODULE scope, not from a component effect,
// because `beforeinstallprompt` fires once and is not replayed. Chrome
// dispatches it after load — normally well after hydration — but a slow render
// on a low-end phone is exactly the case this app is built for, and an event
// that lands before the effect runs would be lost for the whole session, with
// no way to ever show the button. Evaluating this module is the earliest moment
// any client code in the app can listen.

let deferredPrompt: BeforeInstallPromptEvent | null = null;
let installed = false;

const listeners = new Set<() => void>();

function notify(): void {
  for (const listener of listeners) listener();
}

if (typeof window !== 'undefined') {
  window.addEventListener('beforeinstallprompt', (event) => {
    // Suppress Chrome's own mini-infobar so the customer gets one ask, in our
    // wording and our language, rather than two competing ones.
    event.preventDefault();
    deferredPrompt = event as BeforeInstallPromptEvent;
    notify();
  });

  // Fired after an install completes by any route — our button, Chrome's menu,
  // or the address-bar icon. Drop the banner immediately; a prompt to install
  // an app you just installed reads as broken.
  window.addEventListener('appinstalled', () => {
    deferredPrompt = null;
    installed = true;
    notify();
  });
}

/**
 * Subscribe to changes in installability. Returns an unsubscribe function.
 */
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
 * Whether the app is already running installed.
 *
 * <p>`display-mode: standalone` covers Android and desktop; iOS Safari predates
 * that media query for home-screen apps and reports `navigator.standalone`
 * instead, so both are checked.
 */
export function isInstalled(): boolean {
  if (typeof window === 'undefined') return false;
  if (installed) return true;
  try {
    if (window.matchMedia('(display-mode: standalone)').matches) return true;
  } catch {
    /* matchMedia is ancient and universally supported; be safe anyway */
  }
  return (navigator as Navigator & { standalone?: boolean }).standalone === true;
}

/**
 * Whether this is iOS Safari, where install exists but only by hand.
 *
 * <p>Deliberately narrow. Every iOS browser is WebKit underneath, but only
 * Safari has "Add to Home Screen" — Chrome, Firefox and Edge on iOS cannot
 * install at all, so showing them the Share-sheet steps would send the customer
 * hunting for a button that isn't there.
 */
export function isIosSafari(): boolean {
  if (typeof navigator === 'undefined') return false;
  const ua = navigator.userAgent;

  // iPadOS 13+ reports itself as "Macintosh"; the touch points give it away.
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
 * cleared either way and the banner does not come back this session.
 */
export async function promptInstall(): Promise<InstallOutcome> {
  const event = deferredPrompt;
  if (!event) return 'unavailable';

  // Cleared before awaiting: a double-tap would otherwise call prompt() twice
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

// ---- Dismissal and visit counting ----------------------------------------

/** Whether the customer has already declined the banner. */
export function isInstallDismissed(): boolean {
  try {
    return window.localStorage.getItem(DISMISSED_KEY) === '1';
  } catch {
    // No storage means no memory of a dismissal, and a banner we can't
    // remember dismissing would return on every page. Treat it as dismissed.
    return true;
  }
}

/** Remember that the customer declined; the banner does not come back. */
export function rememberInstallDismissed(): void {
  try {
    window.localStorage.setItem(DISMISSED_KEY, '1');
  } catch {
    /* private mode — isInstallDismissed() already errs towards silence */
  }
}

/**
 * Count this visit, once per tab session, and return the running total.
 *
 * <p>"Visit" is a browsing session, not a page view: the storefront is a SPA
 * whose customer moves between category, product and cart pages constantly, and
 * counting those would clear any threshold within a minute of a first arrival —
 * precisely the visitor {@link MIN_VISITS_BEFORE_PROMPT} exists to leave alone.
 * sessionStorage is what draws the line, since it is per tab and dies with it.
 *
 * <p>Returns 0 when storage is unavailable, which keeps the banner hidden.
 */
export function countVisit(): number {
  try {
    const previous = Number(window.localStorage.getItem(VISITS_KEY) ?? '0');
    if (window.sessionStorage.getItem(VISIT_COUNTED_KEY) === '1') {
      return previous;
    }
    const next = previous + 1;
    window.sessionStorage.setItem(VISIT_COUNTED_KEY, '1');
    window.localStorage.setItem(VISITS_KEY, String(next));
    return next;
  } catch {
    return 0;
  }
}
