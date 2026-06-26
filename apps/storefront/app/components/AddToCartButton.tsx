'use client';

import { useState } from 'react';
import { useTranslations } from 'next-intl';
import { ApiError } from '@/app/lib/api';
import type { ProductVariant } from '@/app/lib/types';
import { useCart } from './CartProvider';

/**
 * Real add-to-cart control (M3). On first add it lazily creates a server-side
 * cart (via CartProvider), posts the item, and updates the header badge. Once
 * the variant is in the cart it swaps to quantity steppers (− / +) that drive
 * the server cart; decrementing to 0 removes the line.
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
  const { addItem, decrementVariant, qtyOf } = useCart();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const qty = qtyOf(variant.id);

  if (!variant.available) {
    return (
      <button type="button" className="btn" disabled>
        {t('outOfStock')}
      </button>
    );
  }

  async function run(action: () => Promise<unknown>) {
    setBusy(true);
    setError(null);
    try {
      await action();
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setError(t('notEnoughStock'));
      } else {
        setError(t('couldNotUpdate'));
      }
    } finally {
      setBusy(false);
    }
  }

  if (qty <= 0) {
    return (
      <div className="add-to-cart">
        <button
          type="button"
          className="btn"
          disabled={busy}
          onClick={() => run(() => addItem(variant.id, 1))}
          aria-label={t('ariaAdd', { product: productName, label: variant.label })}
        >
          {busy ? '…' : t('add')}
        </button>
        {error && <span className="add-error">{error}</span>}
      </div>
    );
  }

  return (
    <div className="add-to-cart">
      <div
        className="qty-stepper"
        aria-label={t('ariaQuantity', { product: productName, label: variant.label })}
      >
        <button
          type="button"
          disabled={busy}
          onClick={() => run(() => decrementVariant(variant.id))}
          aria-label={tc('decrease')}
        >
          −
        </button>
        <span className="qty-value" aria-live="polite">
          {qty}
        </span>
        <button
          type="button"
          disabled={busy}
          onClick={() => run(() => addItem(variant.id, 1))}
          aria-label={tc('increase')}
        >
          +
        </button>
      </div>
      {error && <span className="add-error">{error}</span>}
    </div>
  );
}
