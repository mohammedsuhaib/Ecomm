'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { useCallback, useEffect, useState } from 'react';
import { getMyOrders, reorder } from '@/app/lib/api';
import { saveCartId } from '@/app/lib/cart';
import { formatRupees } from '@/app/lib/format';
import { useCartActions } from '@/app/components/CartProvider';
import LiveOrderStamp from './LiveOrderStamp';
import type { Order } from '@/app/lib/types';

/**
 * How many orders show at first, and how many each "Show more" adds.
 *
 * <p>Five, because of what this section is for: the order that is on its way
 * right now, and the one from last week to reorder. Both are at the top. This
 * used to render twenty rows — each with a status stamp and two buttons — in
 * the middle of the account page, so the address book below it was a long
 * scroll away and the page read as an endless list rather than a profile.
 *
 * <p>The same number is the server page size, so a tap is one round trip and
 * the page arithmetic stays simple. A daily shopper with sixty orders taps
 * eleven times to reach the oldest — and the button tells them how many are
 * left each time, so it never feels bottomless. That is the trade against a
 * bigger step, which would make the first expansion a wall of rows on a phone.
 *
 * <p>It also caps live connections: every in-flight row opens an SSE stream
 * plus a poll (see LiveOrderStamp), so five rows bounds that where twenty did
 * not.
 */
const PAGE_SIZE = 5;

/**
 * Recent order history with live status stamps + a per-order "Reorder" action.
 *
 * <p>Shows {@link PAGE_SIZE} orders and a "Show more" button for the rest, not
 * infinite scroll and not numbered pages. Infinite scroll would push the
 * address book below this section out of reach as the list grew; page numbers
 * mean nothing for "the order I placed last Tuesday". A button that names how
 * many older orders remain is explicit, bounded and one thumb away, and it is
 * the pattern the rider app's Completed tab already uses.
 */
export default function OrderHistory() {
  const t = useTranslations('orders');
  const tc = useTranslations('common');
  const router = useRouter();
  const { refresh } = useCartActions();

  const [orders, setOrders] = useState<Order[]>([]);
  // How many the account has in total, from the server's page envelope —
  // what the "Show more" button counts down and what decides when it goes.
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const page = await getMyOrders(0, PAGE_SIZE);
      setOrders(page.content);
      setTotal(page.totalElements);
    } catch {
      setError(t('couldNotLoad'));
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => {
    void load();
  }, [load]);

  async function loadMore() {
    if (loadingMore) return;
    setLoadingMore(true);
    setError(null);
    try {
      // The next page is wherever the loaded list ends. Keyed merge below: an
      // order placed between two taps shifts the paging by one, and the same
      // order must never appear twice.
      const next = await getMyOrders(Math.floor(orders.length / PAGE_SIZE), PAGE_SIZE);
      setOrders((prev) => {
        const seen = new Set(prev.map((o) => o.id));
        return [...prev, ...next.content.filter((o) => !seen.has(o.id))];
      });
      setTotal(next.totalElements);
    } catch {
      // The rows already shown stay; only the expansion failed.
      setError(t('couldNotLoadMore'));
    } finally {
      setLoadingMore(false);
    }
  }

  async function onReorder(id: string) {
    setBusyId(id);
    setError(null);
    try {
      const cart = await reorder(id);
      saveCartId(cart.cartId);
      await refresh();
      router.push('/cart');
    } catch {
      setError(t('couldNotReorder'));
      setBusyId(null);
    }
  }

  if (loading) {
    return (
      <div aria-busy="true" aria-label={t('loadingOrders')}>
        <div className="skeleton-row" />
        <div className="skeleton-row" />
        <div className="skeleton-row" />
      </div>
    );
  }
  if (error && orders.length === 0)
    return <p className="notice error">{error}</p>;
  if (orders.length === 0)
    return <p className="muted">{t('none')}</p>;

  const remaining = Math.max(0, total - orders.length);

  return (
    <>
      {error && <p className="notice error">{error}</p>}
      <ul className="order-history">
        {orders.map((o) => (
          <li key={o.id} className="order-history-row">
            <div className="order-history-info">
              <span className="order-history-id">
                {t('orderNumber', { code: o.publicCode })}
              </span>
              <LiveOrderStamp order={o} />
              <span className="muted">
                {t('itemsCount', {
                  count: o.items.length,
                  total: formatRupees(o.total),
                })}
              </span>
            </div>
            <div className="order-history-actions">
              <Link href={`/order/${o.trackingToken}`} className="link-action">
                {t('view')}
              </Link>
              <button
                type="button"
                className="btn btn-outline"
                disabled={busyId === o.id}
                onClick={() => onReorder(o.id)}
              >
                {busyId === o.id ? t('reordering') : t('reorder')}
              </button>
            </div>
          </li>
        ))}
      </ul>
      {remaining > 0 ? (
        <div className="order-history-more">
          <button
            type="button"
            className="btn btn-outline"
            onClick={loadMore}
            disabled={loadingMore}
          >
            {loadingMore ? tc('loading') : t('showMore', { count: remaining })}
          </button>
        </div>
      ) : (
        // Only once they have actually expanded the list: a customer with three
        // orders does not need telling that three is all of them.
        total > PAGE_SIZE && (
          <p className="muted order-history-end" role="status">
            {t('allShown', { count: total })}
          </p>
        )
      )}
    </>
  );
}
