'use client';

import { useTranslations } from 'next-intl';
import { useEffect, useState } from 'react';
import { getStore } from '@/app/lib/api';
import { formatClock } from '@/app/lib/format';
import type { Store } from '@/app/lib/types';

// Re-check while the app sits open, so the banner appears at closing time and
// clears at opening time without the customer reloading.
const RECHECK_MS = 5 * 60 * 1000;

/**
 * Shown on every page while the store is not serving.
 *
 * The open/closed decision comes from the API, which evaluates the store's
 * hours on the SERVER clock — a phone with the wrong timezone must not be told
 * the shop is open when checkout will refuse the order. It is the same flag
 * checkout enforces, so the two can never disagree.
 *
 * Renders nothing at all while the store is open, or while the status is
 * unknown (offline, API down): a false "we're closed" would cost real orders,
 * so silence is the safer failure.
 */
export default function StoreClosedBanner() {
  const t = useTranslations('common');
  const [store, setStore] = useState<Store | null>(null);

  useEffect(() => {
    let cancelled = false;

    const check = async () => {
      try {
        const next = await getStore({ noStore: true });
        if (!cancelled) setStore(next);
      } catch {
        if (!cancelled) setStore(null); // unknown -> stay quiet
      }
    };

    void check();
    const timer = setInterval(check, RECHECK_MS);
    return () => {
      cancelled = true;
      clearInterval(timer);
    };
  }, []);

  if (!store || store.open) return null;

  const opensAt = formatClock(store.openingTime);

  return (
    <div className="store-closed" role="status">
      <span className="store-closed-icon" aria-hidden>
        🌙
      </span>
      <p className="store-closed-text">
        <strong>{t('storeClosedTitle')}</strong>{' '}
        {store.opensNextDay
          ? t('storeClosedOpensTomorrow', { time: opensAt })
          : t('storeClosedOpensToday', { time: opensAt })}{' '}
        <span className="store-closed-hint">{t('storeClosedBrowseHint')}</span>
      </p>
    </div>
  );
}
