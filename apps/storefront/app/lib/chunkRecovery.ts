/**
 * Recovery from a stale-build chunk failure — the way a deploy breaks an
 * already-open PWA.
 *
 * <p>The failure mode, in order: the service worker sets `skipWaiting` and
 * `clientsClaim` (app/sw.ts), so a new worker activates and claims open clients
 * the moment a deploy lands. The container is built with `output: 'standalone'`,
 * so it holds only the new build's `_next/static` — the previous build's hashed
 * chunks are gone from the server. A page that was loaded before the deploy is
 * still running the old JS, and the next thing it lazily imports (a route
 * chunk, or the map picker behind `next/dynamic`) asks for a filename that no
 * longer exists. Without a boundary that recognises this, React unmounts to
 * Next's bare production error screen — and inside an installed PWA, with no
 * address bar and no tabs, that is a dead end a customer cannot get out of.
 *
 * <p>A reload fixes it completely: the new worker serves the new HTML, which
 * references chunks that exist. So the boundaries call {@link recoverFromChunkError}
 * and the customer sees a flicker instead of a wall.
 *
 * <p><strong>Why the guard is not optional.</strong> A reload loop is worse than
 * the error screen it replaces — it burns battery and data and never resolves.
 * Recovery is therefore attempted at most once per {@link RELOAD_WINDOW_MS}, and
 * when `sessionStorage` is unavailable (private browsing, blocked site data) it
 * declines to reload at all rather than risk looping blind. The caller then
 * shows its error UI, which offers {@link clearCachesAndReload} as the heavier
 * second attempt.
 */

const RELOAD_MARKER = 'tb:chunk-recovery-at';

/**
 * How long a recovery attempt suppresses the next one. Long enough that a
 * reload which lands on the same broken build does not immediately retry,
 * short enough that a genuine second deploy later in the session still heals.
 */
const RELOAD_WINDOW_MS = 30_000;

/**
 * Whether this error is a build asset that has gone missing, rather than a bug
 * in our own code.
 *
 * <p>Matched on both the error name and the message because bundlers and
 * browsers disagree: webpack throws a named `ChunkLoadError`, but a native
 * dynamic `import()` that 404s surfaces as a plain `TypeError` whose wording
 * differs between Chrome, Firefox and Safari. Over-matching here is safe — the
 * worst case is one wasted reload — while under-matching leaves the dead end
 * this module exists to remove.
 */
export function isChunkLoadError(error: unknown): boolean {
  if (!error) return false;
  const name = (error as { name?: unknown }).name;
  if (typeof name === 'string' && name === 'ChunkLoadError') return true;
  const message = (error as { message?: unknown }).message;
  if (typeof message !== 'string') return false;
  return (
    /loading chunk \S+ failed/i.test(message) ||
    /loading css chunk/i.test(message) ||
    /failed to fetch dynamically imported module/i.test(message) ||
    /error loading dynamically imported module/i.test(message) ||
    /importing a module script failed/i.test(message)
  );
}

/**
 * Reload once to pick up the current build.
 *
 * @returns true when a reload was started — the caller should render nothing
 *     further; false when recovery has already been tried (or cannot be tracked),
 *     meaning the caller must show its error UI instead.
 */
export function recoverFromChunkError(): boolean {
  if (typeof window === 'undefined') return false;
  let lastAttempt = 0;
  try {
    lastAttempt = Number(window.sessionStorage.getItem(RELOAD_MARKER) ?? '0');
    if (Number.isFinite(lastAttempt) && Date.now() - lastAttempt < RELOAD_WINDOW_MS) {
      return false;
    }
    window.sessionStorage.setItem(RELOAD_MARKER, String(Date.now()));
  } catch {
    // No session storage means no loop guard, and an unguarded reload on a
    // permanently broken build spins forever. Decline and let the UI show.
    return false;
  }
  window.location.reload();
  return true;
}

/**
 * The heavier second attempt, offered as a button when the automatic reload
 * did not take: drop every service worker and cache entry for this origin, then
 * reload from the network.
 *
 * <p>This is for the case where the stale build is being served *by* the cache —
 * a worker that precached an app shell it can no longer complete, say — so
 * reloading through it just returns the same broken page. Clearing first
 * guarantees the next load comes from the server. Best-effort throughout: a
 * browser that refuses any step still gets the reload.
 */
export async function clearCachesAndReload(): Promise<void> {
  if (typeof window === 'undefined') return;
  try {
    if ('serviceWorker' in navigator) {
      const registrations = await navigator.serviceWorker.getRegistrations();
      await Promise.all(registrations.map((registration) => registration.unregister()));
    }
  } catch {
    // Ignore — the reload below is still worth doing.
  }
  try {
    if ('caches' in window) {
      const keys = await caches.keys();
      await Promise.all(keys.map((key) => caches.delete(key)));
    }
  } catch {
    // Ignore — as above.
  }
  try {
    window.sessionStorage.removeItem(RELOAD_MARKER);
  } catch {
    // Ignore.
  }
  window.location.reload();
}
