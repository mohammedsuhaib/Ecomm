'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useTranslations } from 'next-intl';
import { loadLastOrder, type LastOrder } from '@/app/lib/lastOrder';

/**
 * What the cart and checkout screens show when there is nothing in the basket.
 *
 * <p>"Your cart is empty" is right for a genuinely empty cart and wrong — even
 * alarming — for the customer who just ordered and pressed Back. Placing an
 * order clears the local cart, so that customer lands here, at the moment they
 * most want reassurance, and is told their basket is empty with no mention of
 * the order they just placed. QA reported it as the cart-ordered-twice case.
 *
 * <p>So: if an order was placed in the last half hour, say so and link to it.
 * Otherwise fall back to the plain empty copy. The note is read after mount
 * because localStorage is not available during the server render.
 */
export default function CartEmptyState() {
  const t = useTranslations('cart');
  const tc = useTranslations('common');
  const [lastOrder, setLastOrder] = useState<LastOrder | null>(null);
  useEffect(() => setLastOrder(loadLastOrder()), []);

  if (lastOrder) {
    return (
      <div className="empty-state">
        <div style={{ fontSize: '3rem' }} aria-hidden>
          ✅
        </div>
        {/* h2, not h1: both hosts (cart, checkout) already head the page with
            their own h1, and .section-title carries the size either way. */}
        <h2 className="section-title">{t('alreadyOrderedTitle')}</h2>
        <p>{t('alreadyOrderedBody', { code: lastOrder.code })}</p>
        <p style={{ display: 'flex', gap: '0.6rem', justifyContent: 'center', flexWrap: 'wrap' }}>
          <Link className="btn" href={`/order/${lastOrder.token}`}>
            {t('viewOrder')}
          </Link>
          <Link className="btn btn-outline" href="/">
            {tc('startShopping')}
          </Link>
        </p>
      </div>
    );
  }

  return (
    <div className="empty-state">
      <p>{t('empty')}</p>
      <Link href="/" className="btn">
        {tc('startShopping')}
      </Link>
    </div>
  );
}
