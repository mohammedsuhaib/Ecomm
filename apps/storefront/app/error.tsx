'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useTranslations } from 'next-intl';
import {
  clearCachesAndReload,
  isChunkLoadError,
  recoverFromChunkError,
} from '@/app/lib/chunkRecovery';

/**
 * Error boundary for everything below the root layout — which is every page.
 *
 * <p>Its first job is invisible: a chunk that went missing because a deploy
 * landed under an open PWA session is recovered by reloading, so the customer
 * sees a flicker rather than this screen (see lib/chunkRecovery.ts for why that
 * happens and why the reload is guarded).
 *
 * <p>Its second job is to be a way out. Before this existed, any render error
 * in the storefront fell through to Next's built-in production error screen,
 * which inside an installed PWA — no address bar, no tabs — is somewhere a
 * customer can get stuck mid-checkout with no route back to the shop.
 *
 * <p>Translated, unlike {@link global-error.tsx}: this renders inside the root
 * layout, so next-intl's provider is present. If fetching the messages is
 * itself what failed, this component throws and global-error catches it, which
 * is exactly the backstop it is there to be.
 */
export default function Error({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  const t = useTranslations('errorScreen');
  const tc = useTranslations('common');
  // Rendered only once we know we are not about to reload.
  const [recovering, setRecovering] = useState(() => isChunkLoadError(error));

  useEffect(() => {
    if (isChunkLoadError(error) && recoverFromChunkError()) return;
    setRecovering(false);
  }, [error]);

  if (recovering) {
    // A reload is in flight. Render the loading shape rather than an error the
    // customer will never finish reading.
    return (
      <div className="empty-state" role="status" aria-live="polite">
        <p>{tc('loading')}</p>
      </div>
    );
  }

  return (
    <div className="empty-state">
      <div style={{ fontSize: '3rem' }} aria-hidden>
        🧺
      </div>
      <h1 className="section-title">{t('title')}</h1>
      <p>{t('body')}</p>
      <p style={{ display: 'flex', gap: '0.6rem', justifyContent: 'center', flexWrap: 'wrap' }}>
        <button type="button" className="btn" onClick={reset}>
          {t('tryAgain')}
        </button>
        <Link className="btn btn-outline" href="/">
          {tc('backToShopping')}
        </Link>
      </p>
      {/* Last resort for a wedged service worker: the automatic reload above
          already declined once by the time this is visible. */}
      <p>
        <button
          type="button"
          className="link-button"
          onClick={() => void clearCachesAndReload()}
        >
          {t('clearAndReload')}
        </button>
      </p>
      {error.digest && (
        <p className="muted" style={{ fontSize: '0.8rem' }}>
          {t('reference', { digest: error.digest })}
        </p>
      )}
    </div>
  );
}
