'use client';

import { useState } from 'react';
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
  const { addItem, decrementVariant, qtyOf } = useCart();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const qty = qtyOf(variant.id);

  if (!variant.available) {
    return (
      <button type="button" className="btn" disabled>
        Out of stock
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
        setError('Not enough stock');
      } else {
        setError('Could not update cart');
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
          aria-label={`Add ${productName} (${variant.label}) to cart`}
        >
          {busy ? '…' : 'Add'}
        </button>
        {error && <span className="add-error">{error}</span>}
      </div>
    );
  }

  return (
    <div className="add-to-cart">
      <div
        className="qty-stepper"
        aria-label={`Quantity of ${productName} (${variant.label})`}
      >
        <button
          type="button"
          disabled={busy}
          onClick={() => run(() => decrementVariant(variant.id))}
          aria-label="Decrease quantity"
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
          aria-label="Increase quantity"
        >
          +
        </button>
      </div>
      {error && <span className="add-error">{error}</span>}
    </div>
  );
}
