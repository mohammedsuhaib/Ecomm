'use client';

import { useEffect, useState } from 'react';
import {
  clearCachesAndReload,
  isChunkLoadError,
  recoverFromChunkError,
} from '@/app/lib/chunkRecovery';

// Inlined rather than imported from messages/*.json: this screen must not
// depend on the i18n provider it renders outside of. Kept to the two sentences
// and one button that a stuck customer actually needs.
const COPY = {
  en: {
    updating: 'Updating to the latest version…',
    title: 'Something went wrong',
    body: 'Town Basket couldn’t finish loading. Reloading usually fixes it.',
    reload: 'Reload',
    reference: 'Reference',
  },
  kn: {
    updating: 'ಇತ್ತೀಚಿನ ಆವೃತ್ತಿಗೆ ನವೀಕರಿಸಲಾಗುತ್ತಿದೆ…',
    title: 'ಏನೋ ತಪ್ಪಾಗಿದೆ',
    body: 'Town Basket ಸಂಪೂರ್ಣವಾಗಿ ಲೋಡ್ ಆಗಲಿಲ್ಲ. ಮತ್ತೆ ಲೋಡ್ ಮಾಡಿದರೆ ಸಾಮಾನ್ಯವಾಗಿ ಸರಿಹೋಗುತ್ತದೆ.',
    reload: 'ಮತ್ತೆ ಲೋಡ್ ಮಾಡಿ',
    reference: 'ಉಲ್ಲೇಖ',
  },
} as const;

/** The language the switcher last saved, straight from its cookie (i18n/config.ts). */
function localeFromCookie(): keyof typeof COPY {
  if (typeof document === 'undefined') return 'en';
  const match = /(?:^|;\s*)TB_LOCALE=(en|kn)(?:;|$)/.exec(document.cookie);
  return match ? (match[1] as keyof typeof COPY) : 'en';
}

/**
 * The backstop: an error in the root layout itself, which {@link error.tsx}
 * sits inside and therefore cannot catch.
 *
 * <p>Not a rare path. The location gate lives in the root layout and lazily
 * imports the map picker, so a deploy that removes the old picker chunk fails
 * *in the layout* — verified by removing that chunk from a running build, where
 * this component is what caught it, not error.tsx.
 *
 * <p>Styled inline, and translated from a two-string table rather than
 * next-intl. This component replaces the root layout, so the provider is gone
 * and globals.css may itself be the asset that failed to load — anything this
 * file reaches for is one more thing that can be broken at the moment it is
 * needed. But a Kannada customer should not be handed English at the one screen
 * they are stuck on, so the locale is read straight from the cookie the
 * switcher writes and the copy is inlined. No message fetch, no provider, no
 * new failure surface.
 */
export default function GlobalError({
  error,
}: {
  error: Error & { digest?: string };
}) {
  const [recovering, setRecovering] = useState(() => isChunkLoadError(error));
  // Resolved after mount: document.cookie is not readable during SSR.
  const [locale, setLocale] = useState<keyof typeof COPY>('en');
  useEffect(() => setLocale(localeFromCookie()), []);
  const copy = COPY[locale];

  useEffect(() => {
    if (isChunkLoadError(error) && recoverFromChunkError()) return;
    setRecovering(false);
  }, [error]);

  return (
    <html lang={locale}>
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
              {copy.updating}
            </p>
          ) : (
            <>
              <div style={{ fontSize: '3rem' }} aria-hidden>
                🧺
              </div>
              <h1 style={{ fontSize: '1.2rem', color: '#1b5e20' }}>
                {copy.title}
              </h1>
              <p style={{ color: '#5a6b62', lineHeight: 1.55 }}>
                {copy.body}
              </p>
              <button
                type="button"
                onClick={() => void clearCachesAndReload()}
                style={{
                  marginTop: '0.5rem',
                  padding: '0.7rem 1.4rem',
                  fontSize: '1rem',
                  color: '#ffffff',
                  background: '#2e7d32',
                  border: 'none',
                  borderRadius: 999,
                  cursor: 'pointer',
                }}
              >
                {copy.reload}
              </button>
              {error.digest && (
                <p style={{ color: '#5a6b62', fontSize: '0.8rem' }}>
                  {copy.reference}: {error.digest}
                </p>
              )}
            </>
          )}
        </main>
      </body>
    </html>
  );
}
