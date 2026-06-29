'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { useCallback, useEffect, useState } from 'react';
import { getMyOrders, reorder } from '@/app/lib/api';
import { saveCartId } from '@/app/lib/cart';
import { formatRupees } from '@/app/lib/format';
import { useCart } from '@/app/components/CartProvider';
import LiveOrderStamp from './LiveOrderStamp';
import type { Order } from '@/app/lib/types';

/** Recent order history with live status stamps + a per-order "Reorder" action. */
export default function OrderHistory() {
  const t = useTranslations('orders');
  const router = useRouter();
  const { refresh } = useCart();

  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const page = await getMyOrders(0, 20);
      setOrders(page.content);
    } catch {
      setError(t('couldNotLoad'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

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

  if (loading) return <p className="muted">{t('loadingOrders')}</p>;
  if (error && orders.length === 0)
    return <p className="notice error">{error}</p>;
  if (orders.length === 0)
    return <p className="muted">{t('none')}</p>;

  return (
    <>
      {error && <p className="notice error">{error}</p>}
      <ul className="order-history">
        {orders.map((o) => (
          <li key={o.id} className="order-history-row">
            <div className="order-history-info">
              <span className="order-history-id">
                {t('orderNumber', { id: o.id })}
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
    </>
  );
}
