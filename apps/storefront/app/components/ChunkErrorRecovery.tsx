'use client';

import { useEffect } from 'react';
import { isChunkLoadError, recoverFromChunkError } from '@/app/lib/chunkRecovery';

/**
 * Last line of defence against a blank page after a deploy.
 *
 * <p><strong>Why the error boundary is not enough.</strong> `error.tsx` already
 * recovers from a missing chunk — but only for a failure React sees while
 * rendering. A deploy that lands under an open PWA session breaks the other
 * kind too: the App Router prefetches and lazily imports the chunk for the page
 * you are navigating to, and when that request 404s against the new build the
 * failure surfaces as an unhandled promise rejection in the router, outside any
 * boundary. Nothing renders, nothing catches it, and the customer is looking at
 * white — which is how the cart came up blank once right after a release, since
 * /cart is reached by a client-side navigation and its chunk loads on demand.
 *
 * <p>Both listeners funnel into the same guarded reload as the boundary, so the
 * session-storage guard in {@link recoverFromChunkError} still means at most one
 * reload: whichever path notices first wins and the other is a no-op. A build
 * that is genuinely broken therefore cannot spin.
 *
 * <p>Renders nothing. Mounted high in the root layout so it is listening before
 * the first navigation.
 */
export default function ChunkErrorRecovery() {
  useEffect(() => {
    const handle = (error: unknown) => {
      if (!isChunkLoadError(error)) return;
      // Ignores the return value on purpose: if recovery declines (already
      // tried, or no session storage to track it), there is nothing better to
      // do from here — the error boundary still has its own UI, and a second
      // uncontrolled reload is exactly what the guard exists to prevent.
      recoverFromChunkError();
    };

    // `event.error` is the thrown value where the browser has one; some
    // report only a message string, and isChunkLoadError reads properties off
    // an object, so wrap it rather than passing a bare string it cannot match.
    const onError = (event: ErrorEvent) =>
      handle(event.error ?? { message: event.message });
    const onRejection = (event: PromiseRejectionEvent) => handle(event.reason);

    window.addEventListener('error', onError);
    window.addEventListener('unhandledrejection', onRejection);
    return () => {
      window.removeEventListener('error', onError);
      window.removeEventListener('unhandledrejection', onRejection);
    };
  }, []);

  return null;
}
