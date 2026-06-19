'use client';

import Link from 'next/link';
import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError, getOrder, orderStreamUrl } from '@/app/lib/api';
import { formatRupees } from '@/app/lib/format';
import { useCart } from '@/app/components/CartProvider';
import type { Order, OrderStatus } from '@/app/lib/types';

// Display order + labels for the live status timeline (CANCELLED handled apart).
const STATUS_FLOW: OrderStatus[] = [
  'PLACED',
  'CONFIRMED',
  'PACKING',
  'OUT_FOR_DELIVERY',
  'DELIVERED',
];

const STATUS_LABELS: Record<OrderStatus, string> = {
  PLACED: 'Order placed',
  CONFIRMED: 'Confirmed',
  PACKING: 'Being packed',
  OUT_FOR_DELIVERY: 'Out for delivery',
  DELIVERED: 'Delivered',
  CANCELLED: 'Cancelled',
};

// Big headline (emoji + title) shown at the top — reflects the LIVE status, not a
// fixed "Order placed!".
const STATUS_HEADLINE: Record<OrderStatus, { emoji: string; title: string }> = {
  PLACED: { emoji: '✅', title: 'Order placed!' },
  CONFIRMED: { emoji: '✅', title: 'Order confirmed!' },
  PACKING: { emoji: '📦', title: 'Being packed' },
  OUT_FOR_DELIVERY: { emoji: '🛵', title: 'Out for delivery' },
  DELIVERED: { emoji: '🎉', title: 'Delivered!' },
  CANCELLED: { emoji: '❌', title: 'Order cancelled' },
};

function formatTime(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '';
  return d.toLocaleString('en-IN', {
    day: 'numeric',
    month: 'short',
    hour: 'numeric',
    minute: '2-digit',
  });
}

export default function OrderPage({ params }: { params: { id: string } }) {
  const orderId = params.id;
  const { reset } = useCart();

  const [order, setOrder] = useState<Order | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [live, setLive] = useState(false);
  // Once we've successfully loaded the order, clear the local cart exactly once.
  const cartCleared = useRef(false);

  const applyOrder = useCallback(
    (next: Order) => {
      setOrder(next);
      if (!cartCleared.current) {
        cartCleared.current = true;
        reset();
      }
    },
    [reset],
  );

  // Initial load.
  useEffect(() => {
    let cancelled = false;
    getOrder(orderId)
      .then((o) => {
        if (!cancelled) applyOrder(o);
      })
      .catch((err) => {
        if (cancelled) return;
        setError(
          err instanceof ApiError && err.status === 404
            ? 'We couldn’t find this order.'
            : 'Could not load your order. Please try again.',
        );
      });
    return () => {
      cancelled = true;
    };
  }, [orderId, applyOrder]);

  // Live updates. The order SSE stream emits NAMED "status" events, so we listen
  // for them explicitly (es.onmessage only fires for *unnamed* events and would
  // miss them). We ALSO poll on a steady interval as the reliable fallback: the
  // connection can stay open and silent, so polling — not just onerror — is what
  // guarantees the status advances. Stops once the order is terminal.
  useEffect(() => {
    let pollTimer: ReturnType<typeof setInterval> | null = null;
    let es: EventSource | null = null;
    let stopped = false;

    const stop = () => {
      stopped = true;
      if (pollTimer) {
        clearInterval(pollTimer);
        pollTimer = null;
      }
      if (es) {
        es.close();
        es = null;
      }
    };
    const refetch = () => {
      if (stopped) return;
      getOrder(orderId)
        .then((o) => {
          applyOrder(o);
          if (o.status === 'DELIVERED' || o.status === 'CANCELLED') stop();
        })
        .catch(() => {
          /* transient; keep trying */
        });
    };

    pollTimer = setInterval(refetch, 6000);

    if (typeof EventSource !== 'undefined') {
      try {
        es = new EventSource(orderStreamUrl(orderId));
        es.onopen = () => setLive(true);
        es.addEventListener('status', refetch); // backend pushes NAMED "status" events
        es.onmessage = refetch; // also handle any unnamed events
        es.onerror = () => setLive(false); // browser auto-reconnects; poll keeps us current
      } catch {
        /* polling already covers updates */
      }
    }

    return stop;
  }, [orderId, applyOrder]);

  if (error) {
    return (
      <div className="empty-state">
        <p className="notice error">{error}</p>
        <Link href="/" className="btn">
          Back to shopping
        </Link>
      </div>
    );
  }

  if (!order) {
    return <p className="empty-state">Loading your order…</p>;
  }

  const cancelled = order.status === 'CANCELLED';
  const headline = STATUS_HEADLINE[order.status] ?? STATUS_HEADLINE.PLACED;
  // The delivery code is only useful up to handover — hide it once the order is
  // delivered (the code is spent) or cancelled.
  const showDeliveryCode =
    order.status !== 'DELIVERED' && order.status !== 'CANCELLED';
  const currentIndex = STATUS_FLOW.indexOf(order.status);
  // Map each status to the time it was reached, from the timeline.
  const reachedAt = new Map(order.timeline.map((t) => [t.toStatus, t.at]));

  return (
    <>
      <div className="order-confirm">
        <div className="big-emoji" aria-hidden>
          {headline.emoji}
        </div>
        <h1>{headline.title}</h1>
        <p className="muted">
          Order #{order.id} · {formatTime(order.placedAt)}
        </p>
      </div>

      {showDeliveryCode && (
        <div className="otp-card">
          <span className="otp-label">Delivery code</span>
          <span className="otp-code">{order.deliveryOtp}</span>
          <span className="muted otp-hint">
            Share this code with the delivery person at handover.
          </span>
        </div>
      )}

      <section>
        <h2 className="section-title">
          Order status{' '}
          <span className={`live-dot ${live ? 'on' : ''}`} title={live ? 'Live' : 'Reconnecting'}>
            {live ? '● live' : '○ live'}
          </span>
        </h2>

        {cancelled ? (
          <p className="notice error">
            This order was cancelled. Any reserved items have been released.
          </p>
        ) : (
          <ol className="status-timeline">
            {STATUS_FLOW.map((status, i) => {
              const done = i <= currentIndex;
              const current = i === currentIndex;
              const at = reachedAt.get(status);
              return (
                <li
                  key={status}
                  className={`status-step ${done ? 'done' : ''} ${current ? 'current' : ''}`}
                >
                  <span className="status-marker" aria-hidden>
                    {done ? '✓' : ''}
                  </span>
                  <span className="status-text">
                    <span className="status-name">{STATUS_LABELS[status]}</span>
                    {at && <span className="muted status-at">{formatTime(at)}</span>}
                  </span>
                </li>
              );
            })}
          </ol>
        )}
      </section>

      <section>
        <h2 className="section-title">Order summary</h2>
        <ul className="order-items">
          {order.items.map((item, idx) => (
            <li key={idx} className="order-item-row">
              <span>
                {item.productName}{' '}
                <span className="muted">
                  ({item.label}) × {item.qty}
                </span>
              </span>
              <span>{formatRupees(item.lineTotal)}</span>
            </li>
          ))}
        </ul>
        <div className="cart-summary">
          <div className="cart-summary-row">
            <span>Subtotal</span>
            <span>{formatRupees(order.subtotal)}</span>
          </div>
          <div className="cart-summary-row total">
            <span>Total</span>
            <strong>{formatRupees(order.total)}</strong>
          </div>
          <div className="cart-summary-row">
            <span>Payment</span>
            <span>
              {order.paymentMethod === 'COD' ? 'Cash on Delivery' : 'UPI'} ·{' '}
              {order.paymentStatus}
            </span>
          </div>
        </div>
      </section>

      <section className="order-address">
        <h2 className="section-title">Delivering to</h2>
        <p>
          <strong>{order.customerName}</strong> · {order.phone}
          <br />
          {order.address.line}
        </p>
      </section>

      <Link href="/" className="btn btn-outline btn-block">
        Continue shopping
      </Link>
    </>
  );
}
