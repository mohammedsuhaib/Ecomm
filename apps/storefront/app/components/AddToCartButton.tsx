'use client';

import { useTranslations } from 'next-intl';
import type { ProductVariant } from '@/app/lib/types';
import { useCart } from './CartProvider';

/**
 * Real add-to-cart control (M3). On first add it lazily creates a server-side
 * cart (via CartProvider), posts the item, and updates the header badge. Once
 * the variant is in the cart it swaps to quantity steppers (− / +) that drive
 * the server cart; decrementing to 0 removes the line.
 *
 * <p>Taps are applied locally and the server call is coalesced by CartProvider,
 * so the buttons stay live during the round-trip. They used to disable
 * themselves for its duration, which dropped every tap after the first — the
 * customer aiming for 4 packs on a slow connection got 1.
 */
export default function AddToCartButton({
  variant,
  productName,
}: {
  variant: ProductVariant;
  productName: string;
}) {
  const t = useTranslations('addToCart');
  const tc = useTranslations('common');
  const { nudgeVariant, qtyOf, errorOf } = useCart();

  const qty = qtyOf(variant.id);
  const error = errorOf(variant.id);
  const message =
    error === 'stock' ? t('notEnoughStock') : error ? t('couldNotUpdate') : null;

  // Out of stock = store toggled it off OR inventory has nothing sellable left.
  if (!variant.available || variant.availableStock <= 0) {
    return (
      <button type="button" className="btn" disabled>
        {t('outOfStock')}
      </button>
    );
  }

  if (qty <= 0) {
    return (
      <div className="add-to-cart">
        <button
          type="button"
          className="btn"
          onClick={() => nudgeVariant(variant.id, 1)}
          aria-label={t('ariaAdd', { product: productName, label: variant.label })}
        >
          {t('add')}
        </button>
        {message && <span className="add-error">{message}</span>}
      </div>
    );
  }

  const atStockLimit = qty >= variant.availableStock;

  return (
    <div className="add-to-cart">
      <div
        className="qty-stepper"
        aria-label={t('ariaQuantity', { product: productName, label: variant.label })}
      >
        <button
          type="button"
          onClick={() => nudgeVariant(variant.id, -1)}
          aria-label={tc('decrease')}
        >
          −
        </button>
        <span className="qty-value" aria-live="polite">
          {qty}
        </span>
        <button
          type="button"
          disabled={atStockLimit}
          title={atStockLimit ? t('notEnoughStock') : undefined}
          onClick={() => nudgeVariant(variant.id, 1)}
          aria-label={tc('increase')}
        >
          +
        </button>
      </div>
      {message && <span className="add-error">{message}</span>}
    </div>
  );
}
