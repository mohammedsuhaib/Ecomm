'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useTranslations } from 'next-intl';
import { ApiError, getOrder } from '@/app/lib/api';
import { clearLastOrder, loadLastOrder, type LastOrder } from '@/app/lib/lastOrder';

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
 * Otherwise fall back to the plain empty copy.
 *
 * <p>The note is a local memory of the order being PLACED; it knows nothing of
 * what happened since. So it is checked against the server before being
 * trusted: an order the customer cancelled a minute later, or that the shop
 * cancelled while they were away, must not greet them here as "Your order is
 * placed — track this order". That was the reported bug. While the check is in
 * flight the screen says it is loading — never "empty", for the reason above.
 */
export default function CartEmptyState() {
  const t = useTranslations('cart');
  const tc = useTranslations('common');
  // undefined = not decided yet (before mount, and while the order is being
  // checked); null = nothing to show beyond the plain empty state.
  const [lastOrder, setLastOrder] = useState<LastOrder | null | undefined>(undefined);

  useEffect(() => {
    const note = loadLastOrder();
    if (!note) {
      setLastOrder(null);
      return;
    }
    let stale = false;
    getOrder(note.token)
      .then((order) => {
        if (stale) return;
        if (order.status === 'CANCELLED') {
          // Nothing is coming, so there is nothing to track — and the note
          // would otherwise keep reviving this screen for another half hour.
          clearLastOrder();
          setLastOrder(null);
          return;
        }
        setLastOrder(note);
      })
      .catch((err) => {
        if (stale) return;
        if (err instanceof ApiError && err.status === 404) {
          // The order is not this account's to see (or no longer exists):
          // a link to it would lead nowhere useful.
          clearLastOrder();
          setLastOrder(null);
          return;
        }
        // Anything else — offline, a session mid-refresh, a 5xx — is not
        // evidence against the order. Show the note; the tracking page will
        // say what is actually going on.
        setLastOrder(note);
      });
    return () => {
      stale = true;
    };
  }, []);

  if (lastOrder === undefined) {
    return (
      <div className="empty-state">
        <p>{t('loading')}</p>
      </div>
    );
  }

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
