'use client';

import { useEffect, useState } from 'react';
import {
  clearCachesAndReload,
  isChunkLoadError,
  recoverFromChunkError,
} from '@/app/lib/chunkRecovery';

/**
 * Error boundary for everything below the root layout — which is every page of
 * the delivery app.
 *
 * <p>Its first job is invisible: a chunk that went missing because a deploy
 * landed under an app open for a rider's whole shift is recovered by reloading, so the user sees a
 * flicker rather than this screen (see lib/chunkRecovery.ts for the mechanism
 * and why the reload is guarded).
 *
 * <p>Its second job is to be a way out. Before this existed, any render error
 * fell through to Next's built-in production error screen, which offers no
 * route back into the app.
 */
export default function Error({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  const [recovering, setRecovering] = useState(() => isChunkLoadError(error));

  useEffect(() => {
    if (isChunkLoadError(error) && recoverFromChunkError()) return;
    setRecovering(false);
  }, [error]);

  if (recovering) {
    return (
      <p className="queue-empty" role="status" aria-live="polite">
        Updating to the latest version…
      </p>
    );
  }

  return (
    <div className="queue-empty">
      <h1 style={{ fontSize: '1.15rem' }}>Something went wrong</h1>
      <p>That didn’t load properly. Trying again usually fixes it.</p>
      <p style={{ display: 'flex', gap: '0.6rem', justifyContent: 'center', flexWrap: 'wrap' }}>
        <button type="button" className="btn" onClick={reset}>
          Try again
        </button>
        <button
          type="button"
          className="btn btn-ghost"
          onClick={() => void clearCachesAndReload()}
        >
          Clear cache and reload
        </button>
      </p>
      {error.digest && (
        <p style={{ fontSize: '0.8rem' }}>Reference: {error.digest}</p>
      )}
    </div>
  );
}
