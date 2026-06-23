'use client';

import Link from 'next/link';
import { useTranslations } from 'next-intl';
import { useCart } from './CartProvider';

/** Header cart link with a live item-count badge. */
export default function CartIndicator() {
  const { itemCount } = useCart();
  const t = useTranslations('cartIndicator');
  const tc = useTranslations('common');
  return (
    <Link
      href="/cart"
      className="cart-pill"
      aria-label={t('aria', { count: itemCount })}
    >
      <span aria-hidden>🛒</span>
      <span className="cart-label">{tc('cart')}</span>
      {itemCount > 0 && (
        <span className="cart-badge" aria-hidden>
          {itemCount}
        </span>
      )}
    </Link>
  );
}
