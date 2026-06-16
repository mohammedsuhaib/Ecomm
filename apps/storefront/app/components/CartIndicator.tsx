'use client';

import Link from 'next/link';
import { useCart } from './CartProvider';

/** Header cart link with a live item-count badge. */
export default function CartIndicator() {
  const { itemCount } = useCart();
  return (
    <Link
      href="/cart"
      className="cart-pill"
      aria-label={`Cart, ${itemCount} item${itemCount === 1 ? '' : 's'}`}
    >
      <span aria-hidden>🛒</span>
      <span className="cart-label">Cart</span>
      {itemCount > 0 && (
        <span className="cart-badge" aria-hidden>
          {itemCount}
        </span>
      )}
    </Link>
  );
}
