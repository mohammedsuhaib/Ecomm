'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { formatRupees } from '@/app/lib/format';
import { useCart } from './CartProvider';
import { useLocationGate } from './LocationGate';

/** Pages where the bar would be redundant or in the way. */
const HIDDEN_ON = ['/cart', '/checkout', '/order', '/account'];

/**
 * A bar fixed to the bottom of the screen on phones once something is in the
 * basket: how many items, what they come to, and one big tap to the cart.
 *
 * <p>The header cart button scrolls out of sight as soon as the customer moves
 * down a product list, so "where is my cart?" meant scrolling back to the top.
 * This keeps the way to checkout under the thumb wherever they are, which is
 * how the quick-commerce apps customers already know behave.
 *
 * <p>Phones only (CSS hides it from 768px up, where the header button stays in
 * view). It renders nothing for an empty basket, on the cart/checkout/order/
 * account pages, or while the location prompt is blocking the shop. A spacer of
 * the same height is rendered with it so the footer and the last products are
 * never hidden behind it.
 */
export default function StickyCartBar() {
  const { cart, itemCount } = useCart();
  const { blocking } = useLocationGate();
  const pathname = usePathname();
  const t = useTranslations('stickyCart');

  if (itemCount <= 0 || blocking) return null;
  if (HIDDEN_ON.some((p) => pathname === p || pathname.startsWith(`${p}/`))) {
    return null;
  }

  const total = cart?.subtotal ?? null;

  return (
    <>
      <div className="sticky-cart-spacer" aria-hidden />
      <Link href="/cart" className="sticky-cart-bar">
        <span className="sticky-cart-summary">
          <span className="sticky-cart-count">{t('items', { count: itemCount })}</span>
          {total !== null && <span className="sticky-cart-total">{formatRupees(total)}</span>}
        </span>
        <span className="sticky-cart-cta">
          {t('view')}
          <span aria-hidden> ›</span>
        </span>
      </Link>
    </>
  );
}
