/**
 * Recovery from a stale-build chunk failure — the way a deploy breaks a window
 * that was already open.
 *
 * <p>The admin dashboard is not a service-worker PWA, so nothing here is about
 * caching. The mechanism is simpler and just as real: a staff member leaves the
 * dashboard open on the counter tablet all day, a deploy lands, and the
 * container is rebuilt with `output: 'standalone'` so it holds only the new
 * build's `_next/static`. The page is still running the old JS, and the next
 * route chunk it asks for — switching to the Products tab, opening a product
 * form — is a filename the server no longer has. Without a boundary that
 * recognises this, React unmounts to Next's bare production error screen in the
 * middle of a shift.
 *
 * <p>A reload fixes it completely, because the reload fetches the new HTML with
 * the new chunk names. So the boundaries call {@link recoverFromChunkError} and
 * the user sees a flicker instead of a wall.
 *
 * <p><strong>Why the guard is not optional.</strong> A reload loop is worse than
 * the error screen it replaces — it makes the tablet useless rather than merely
 * stuck. Recovery is therefore attempted at most once per
 * {@link RELOAD_WINDOW_MS}, and when `sessionStorage` is unavailable it declines
 * to reload at all rather than risk looping blind. The caller then shows its
 * error UI, which offers {@link clearCachesAndReload} as the heavier second
 * attempt.
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
 * <p>There is no service worker in this app today, so in practice this mostly
 * bypasses the HTTP cache — but it is written to handle one anyway, because a
 * worker added later would otherwise turn this button into a no-op without
 * anything failing. Best-effort throughout: a browser that refuses any step
 * still gets the reload.
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
