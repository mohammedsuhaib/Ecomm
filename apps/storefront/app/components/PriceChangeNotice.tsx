'use client';

import { useLocale, useTranslations } from 'next-intl';
import { formatRupees } from '@/app/lib/format';
import { lineDisplayName } from '@/app/lib/productName';
import { useCart } from '@/app/components/CartProvider';

/**
 * Tells the customer that a line's price moved while it sat in their cart, and
 * makes them accept the new prices before they can carry on.
 *
 * Cart prices are re-read live from the catalogue, so a store admin's edit
 * would otherwise just appear in the total with nothing said about it. Naming
 * each item and its old and new price is the point: "the total changed" leaves
 * the customer to work out what changed and by how much.
 *
 * Renders nothing when no price has moved, so both the cart and checkout pages
 * can include it unconditionally.
 */
export default function PriceChangeNotice() {
  const t = useTranslations('priceChange');
  const locale = useLocale();
  const { priceChanges, acknowledgePriceChanges } = useCart();

  if (priceChanges.length === 0) return null;

  return (
    <div className="notice warn" role="alert">
      <p>
        <strong>{t('title')}</strong> {t('body')}
      </p>
      <ul className="price-change-list">
        {priceChanges.map((change) => (
          <li key={change.variantId}>
            {lineDisplayName(change, locale)}{' '}
            <span className="muted">({change.label})</span>{' '}
            {t('wasNow', {
              was: formatRupees(change.was),
              now: formatRupees(change.now),
            })}
          </li>
        ))}
      </ul>
      <button type="button" className="btn" onClick={acknowledgePriceChanges}>
        {t('accept')}
      </button>
    </div>
  );
}
