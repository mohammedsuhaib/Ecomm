'use client';

import { useLocale, useTranslations } from 'next-intl';
import type { Product } from '@/app/lib/types';
import { productDisplayName } from '@/app/lib/productName';
import { cheapestBuyableVariant } from '@/app/lib/variants';
import { useCart } from './CartProvider';

/**
 * Inline "+" quick-add control for product grid tiles (F3). Adds the SAME
 * variant the card prices — the cheapest buyable one (see lib/variants.ts), so
 * tapping + can never cart a different pack than the tile shows — and once in
 * the cart it swaps to a compact
 * −/+ stepper (same server-cart patterns as AddToCartButton). Rendered as an
 * overlay on the card thumb, so it stops click/navigation bubbling to the card
 * link. Renders nothing when the product has no buyable variant.
 *
 * <p>The buttons are never disabled while a request is in flight. They used to
 * be, which lost every tap after the first: the quantity is now applied locally
 * on the tap and the server call is coalesced by CartProvider, so tapping +
 * four times shows 4 straight away and sends one request. That also removes the
 * local busy/error state this component used to keep — it renders from the cart
 * alone.
 */
export default function QuickAddButton({ product }: { product: Product }) {
  const t = useTranslations('quickAdd');
  const tc = useTranslations('common');
  const locale = useLocale();
  const displayName = productDisplayName(product, locale);
  const { nudgeVariant, qtyOf, errorOf } = useCart();

  const variant = cheapestBuyableVariant(product);

  // No buyable variant (unavailable or out of stock) => no quick-add control
  // (card still links to the detail page).
  if (!variant) return null;

  const variantId = variant.id;
  const qty = qtyOf(variantId);
  const error = errorOf(variantId);
  const failureTitle =
    error === 'stock' ? t('notEnoughStock') : error ? t('couldNotAdd') : undefined;

  // Keep taps on the control from triggering the surrounding card <Link>.
  function stop(e: React.MouseEvent) {
    e.preventDefault();
    e.stopPropagation();
  }

  function nudge(e: React.MouseEvent, delta: number) {
    stop(e);
    nudgeVariant(variantId, delta);
  }

  if (qty <= 0) {
    return (
      <button
        type="button"
        className="quick-add"
        onClick={(e) => nudge(e, 1)}
        aria-label={t('ariaAdd', { product: displayName })}
        title={failureTitle ?? t('addToCart')}
      >
        +
      </button>
    );
  }

  const atStockLimit = qty >= variant.availableStock;

  return (
    <div
      className="quick-add-stepper"
      onClick={stop}
      aria-label={t('ariaQuantity', { product: displayName })}
    >
      <button type="button" onClick={(e) => nudge(e, -1)} aria-label={tc('decrease')}>
        −
      </button>
      <span className="qty-value" aria-live="polite">
        {qty}
      </span>
      <button
        type="button"
        disabled={atStockLimit}
        title={atStockLimit ? t('notEnoughStock') : failureTitle}
        onClick={(e) => nudge(e, 1)}
        aria-label={tc('increase')}
      >
        +
      </button>
    </div>
  );
}
