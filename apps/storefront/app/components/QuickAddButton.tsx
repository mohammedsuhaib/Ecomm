'use client';

import { useState } from 'react';
import { ApiError } from '@/app/lib/api';
import type { Product } from '@/app/lib/types';
import { useCart } from './CartProvider';

/**
 * Inline "+" quick-add control for product grid tiles (F3). Adds the product's
 * first AVAILABLE variant to the cart; once in the cart it swaps to a compact
 * −/+ stepper (same server-cart patterns as AddToCartButton). Rendered as an
 * overlay on the card thumb, so it stops click/navigation bubbling to the card
 * link. Renders nothing when the product has no buyable variant.
 */
export default function QuickAddButton({ product }: { product: Product }) {
  const { addItem, decrementVariant, qtyOf } = useCart();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(false);

  const variant =
    product.available !== false
      ? (product.variants ?? []).find((v) => v.available)
      : undefined;

  // No buyable variant => no quick-add control (card still links to detail).
  if (!variant) return null;

  const qty = qtyOf(variant.id);

  // Keep taps on the control from triggering the surrounding card <Link>.
  function stop(e: React.MouseEvent) {
    e.preventDefault();
    e.stopPropagation();
  }

  async function run(action: () => Promise<unknown>) {
    setBusy(true);
    setError(false);
    try {
      await action();
    } catch (err) {
      // Surface stock issues briefly; any error just flags the control.
      setError(err instanceof ApiError);
    } finally {
      setBusy(false);
    }
  }

  if (qty <= 0) {
    return (
      <button
        type="button"
        className="quick-add"
        disabled={busy}
        onClick={(e) => {
          stop(e);
          void run(() => addItem(variant.id, 1));
        }}
        aria-label={`Add ${product.name} to cart`}
        title={error ? 'Could not add — try again' : 'Add to cart'}
      >
        {busy ? '…' : '+'}
      </button>
    );
  }

  return (
    <div
      className="quick-add-stepper"
      onClick={stop}
      aria-label={`Quantity of ${product.name} in cart`}
    >
      <button
        type="button"
        disabled={busy}
        onClick={(e) => {
          stop(e);
          void run(() => decrementVariant(variant.id));
        }}
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
        onClick={(e) => {
          stop(e);
          void run(() => addItem(variant.id, 1));
        }}
        aria-label="Increase quantity"
      >
        +
      </button>
    </div>
  );
}
