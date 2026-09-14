'use client';

import { useEffect, useState } from 'react';
import {
  clearCachesAndReload,
  isChunkLoadError,
  recoverFromChunkError,
} from '@/app/lib/chunkRecovery';

/**
 * The backstop: an error in the root layout itself, which {@link error.tsx}
 * sits inside and therefore cannot catch.
 *
 * <p>Styled inline on purpose. This replaces the root layout, so globals.css
 * may be the very asset that failed to load — anything this file depends on is
 * one more thing that can be broken at the moment it is needed. Two sentences
 * and a working button beat a pretty screen that might not render.
 */
export default function GlobalError({
  error,
}: {
  error: Error & { digest?: string };
}) {
  const [recovering, setRecovering] = useState(() => isChunkLoadError(error));

  useEffect(() => {
    if (isChunkLoadError(error) && recoverFromChunkError()) return;
    setRecovering(false);
  }, [error]);

  return (
    <html lang="en">
      <body
        style={{
          margin: 0,
          minHeight: '100vh',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          padding: '1.5rem',
          fontFamily:
            "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif",
          color: '#1f2a24',
          background: '#ffffff',
          textAlign: 'center',
        }}
      >
        <main style={{ maxWidth: '26rem' }}>
          {recovering ? (
            <p role="status" aria-live="polite">
              Updating to the latest version…
            </p>
          ) : (
            <>
              <h1 style={{ fontSize: '1.2rem' }}>Town Basket Delivery couldn’t load</h1>
              <p style={{ color: '#5a6b62', lineHeight: 1.55 }}>
                Reloading usually fixes it.
              </p>
              <button
                type="button"
                onClick={() => void clearCachesAndReload()}
                style={{
                  marginTop: '0.5rem',
                  padding: '0.7rem 1.4rem',
                  fontSize: '1rem',
                  color: '#ffffff',
                  background: '#1a56db',
                  border: 'none',
                  borderRadius: 999,
                  cursor: 'pointer',
                }}
              >
                Reload
              </button>
              {error.digest && (
                <p style={{ color: '#5a6b62', fontSize: '0.8rem' }}>
                  Reference: {error.digest}
                </p>
              )}
            </>
          )}
        </main>
      </body>
    </html>
  );
}
