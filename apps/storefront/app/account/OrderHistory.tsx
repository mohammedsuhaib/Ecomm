'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useCallback, useEffect, useState } from 'react';
import { getMyOrders, reorder } from '@/app/lib/api';
import { saveCartId } from '@/app/lib/cart';
import { formatRupees } from '@/app/lib/format';
import { useCart } from '@/app/components/CartProvider';
import type { Order } from '@/app/lib/types';

function formatTime(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '';
  return d.toLocaleString('en-IN', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  });
}

const STATUS_LABELS: Record<string, string> = {
  PLACED: 'Order placed',
  CONFIRMED: 'Confirmed',
  PACKING: 'Being packed',
  OUT_FOR_DELIVERY: 'Out for delivery',
  DELIVERED: 'Delivered',
  CANCELLED: 'Cancelled',
};

/** Recent order history with a per-order "Reorder" action. */
export default function OrderHistory() {
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
      setError('Could not load your orders.');
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
      setError('Could not start a reorder. Some items may be unavailable.');
      setBusyId(null);
    }
  }

  if (loading) return <p className="muted">Loading your orders…</p>;
  if (error && orders.length === 0)
    return <p className="notice error">{error}</p>;
  if (orders.length === 0)
    return <p className="muted">You haven’t placed any orders yet.</p>;

  return (
    <>
      {error && <p className="notice error">{error}</p>}
      <ul className="order-history">
        {orders.map((o) => (
          <li key={o.id} className="order-history-row">
            <div className="order-history-info">
              <span className="order-history-id">Order #{o.id}</span>
              <span className="muted">{formatTime(o.placedAt)}</span>
              <span className="muted">
                {STATUS_LABELS[o.status] ?? o.status} ·{' '}
                {o.items.length} item{o.items.length === 1 ? '' : 's'} ·{' '}
                {formatRupees(o.total)}
              </span>
            </div>
            <div className="order-history-actions">
              <Link href={`/order/${o.id}`} className="link-action">
                View
              </Link>
              <button
                type="button"
                className="btn btn-outline"
                disabled={busyId === o.id}
                onClick={() => onReorder(o.id)}
              >
                {busyId === o.id ? 'Reordering…' : 'Reorder'}
              </button>
            </div>
          </li>
        ))}
      </ul>
    </>
  );
}
