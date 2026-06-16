'use client';

import { useState } from 'react';
import type { ProductVariant } from '@/app/lib/types';

/**
 * Add-to-cart placeholder. The cart module is M3 — for now this stubs the
 * interaction (disabled when the variant is unavailable) and shows a brief
 * acknowledgement so the flow is demoable. No state is persisted yet.
 */
export default function AddToCartButton({
  variant,
  productName,
}: {
  variant: ProductVariant;
  productName: string;
}) {
  const [added, setAdded] = useState(false);

  function onAdd() {
    // TODO(M3): call the cart API (server-side cart keyed to user).
    setAdded(true);
    setTimeout(() => setAdded(false), 1500);
  }

  if (!variant.available) {
    return (
      <button type="button" className="btn" disabled>
        Out of stock
      </button>
    );
  }

  return (
    <button
      type="button"
      className="btn"
      onClick={onAdd}
      aria-label={`Add ${productName} (${variant.label}) to cart`}
    >
      {added ? '✓ Added' : 'Add'}
    </button>
  );
}
