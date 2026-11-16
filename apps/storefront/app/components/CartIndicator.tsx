'use client';

import Link from 'next/link';
import { useTranslations } from 'next-intl';
import { useCart } from './CartProvider';

/**
 * Header cart link with a live item-count badge.
 *
 * <p>A solid white pill with a drawn cart icon and the word "Cart" at every width.
 * It used to be a translucent green pill holding an emoji, which is low contrast
 * against the green header (the emoji's own colours fight it), carried no label
 * on a phone, and was a small tap target — on the screen where the cart matters
 * most. The count badge is a saturated accent so it reads at a glance.
 */
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
      <svg
        className="cart-icon"
        viewBox="0 0 24 24"
        width="22"
        height="22"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
        aria-hidden
      >
        <circle cx="9" cy="20" r="1.5" />
        <circle cx="18" cy="20" r="1.5" />
        <path d="M2 3h3l2.6 12.2a2 2 0 0 0 2 1.6h8.1a2 2 0 0 0 2-1.5L21.5 8H6.1" />
      </svg>
      <span className="cart-label">{tc('cart')}</span>
      {itemCount > 0 && (
        <span className="cart-badge" aria-hidden>
          {itemCount}
        </span>
      )}
    </Link>
  );
}
