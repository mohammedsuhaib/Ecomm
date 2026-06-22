'use client';

import Link from 'next/link';
import { useTranslations } from 'next-intl';
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

// Emoji shown in the big headline — reflects the LIVE status. The titles are
// translated via the 'order' namespace (see HEADLINE_KEY).
const STATUS_EMOJI: Record<OrderStatus, string> = {
  PLACED: '✅',
  CONFIRMED: '✅',
  PACKING: '📦',
  OUT_FOR_DELIVERY: '🛵',
  DELIVERED: '🎉',
  CANCELLED: '❌',
};

const HEADLINE_KEY = {
  PLACED: 'headlinePlaced',
  CONFIRMED: 'headlineConfirmed',
  PACKING: 'headlinePacking',
  OUT_FOR_DELIVERY: 'headlineOutForDelivery',
  DELIVERED: 'headlineDelivered',
  CANCELLED: 'headlineCancelled',
} as const satisfies Record<OrderStatus, string>;

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
  // The route param is the unguessable tracking token, not the numeric id.
  const trackingToken = params.id;
  const { reset } = useCart();
  const t = useTranslations('order');
  const ts = useTranslations('orderStatus');
  const tc = useTranslations('common');
  const tCheckout = useTranslations('checkout');

  const [order, setOrder] = useState<Order | null>(null);
  const [error, setError] = useState<string | null>(null);
  // 'connecting' until the first successful read; then 'live' (SSE open) or
  // 'polling' (SSE unavailable but we're still refreshing every few seconds).
  const [conn, setConn] = useState<'connecting' | 'live' | 'polling'>('connecting');
  const [lastUpdated, setLastUpdated] = useState<number | null>(null);
  // Once we've successfully loaded the order, clear the local cart exactly once.
  const cartCleared = useRef(false);
  // The numeric id (from the fetched order) keys the SSE stream.
  const streamId = order?.id ?? null;

  const applyOrder = useCallback(
    (next: Order) => {
      setOrder(next);
      setLastUpdated(Date.now());
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
    getOrder(trackingToken)
      .then((o) => {
        if (!cancelled) applyOrder(o);
      })
      .catch((err) => {
        if (cancelled) return;
        setError(
          err instanceof ApiError && err.status === 404
            ? t('notFound')
            : t('couldNotLoad'),
        );
      });
    return () => {
      cancelled = true;
    };
  }, [trackingToken, applyOrder]);

  // Live updates: subscribe to the order SSE stream (keyed by the resolved
  // numeric id) and ALSO poll by the unguessable tracking token as the reliable
  // fallback (ARCHITECTURE §3.8). The order SSE stream emits NAMED "status"
  // events, so we listen for them explicitly (es.onmessage only fires for
  // *unnamed* events and would miss them). Polling — not just onerror — is what
  // guarantees the status advances, since the connection can stay open and
  // silent. Runs once the order id is known; stops once the order is terminal.
  useEffect(() => {
    if (streamId == null) return;
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
      getOrder(trackingToken)
        .then((o) => {
          applyOrder(o);
          if (o.status === 'DELIVERED' || o.status === 'CANCELLED') stop();
        })
        .catch(() => {
          /* transient; keep trying */
        });
    };

    // Steady polling fallback so the customer keeps getting updates even if SSE
    // never opens or stays silent.
    setConn((c) => (c === 'live' ? c : 'polling'));
    pollTimer = setInterval(refetch, 6000);

    if (typeof EventSource !== 'undefined') {
      try {
        es = new EventSource(orderStreamUrl(String(streamId)));
        es.onopen = () => setConn('live');
        es.addEventListener('status', refetch); // backend pushes NAMED "status" events
        es.onmessage = refetch; // also handle any unnamed events
        es.onerror = () => {
          // Browser auto-reconnects EventSource; meanwhile keep polling so we
          // stay current.
          setConn((c) => (c === 'live' ? 'polling' : c));
        };
      } catch {
        /* polling already covers updates */
      }
    }

    return stop;
  }, [streamId, trackingToken, applyOrder]);

  if (error) {
    return (
      <div className="empty-state">
        <p className="notice error">{error}</p>
        <Link href="/" className="btn">
          {tc('backToShopping')}
        </Link>
      </div>
    );
  }

  if (!order) {
    return <p className="empty-state">{t('loadingOrder')}</p>;
  }

  const cancelled = order.status === 'CANCELLED';
  const headlineEmoji = STATUS_EMOJI[order.status] ?? STATUS_EMOJI.PLACED;
  const headlineTitle = t(HEADLINE_KEY[order.status] ?? 'headlinePlaced');
  const currentIndex = STATUS_FLOW.indexOf(order.status);
  // Map each status to the time it was reached, from the timeline.
  const reachedAt = new Map(order.timeline.map((t) => [t.toStatus, t.at]));

  return (
    <>
      <div className="order-confirm">
        <div className="big-emoji" aria-hidden>
          {headlineEmoji}
        </div>
        <h1>{headlineTitle}</h1>
        <p className="muted">
          {t('orderNumberTime', {
            id: order.id,
            time: formatTime(order.placedAt),
          })}
        </p>
      </div>

      {order.status === 'OUT_FOR_DELIVERY' && order.deliveryOtp && (
        <div className="otp-card">
          <span className="otp-label">{t('deliveryCode')}</span>
          <span className="otp-code">{order.deliveryOtp}</span>
          <span className="muted otp-hint">{t('deliveryCodeHintOnTheWay')}</span>
        </div>
      )}

      <section>
        <h2 className="section-title">
          {t('orderStatusHeading')}{' '}
          <span
            className={`live-dot ${conn === 'live' ? 'on' : ''}`}
            title={
              conn === 'live'
                ? t('connLiveTitle')
                : conn === 'polling'
                  ? t('connPollingTitle')
                  : t('connConnectingTitle')
            }
          >
            {conn === 'live'
              ? `● ${t('connLive')}`
              : conn === 'polling'
                ? `↻ ${t('connUpdating')}`
                : `○ ${t('connConnecting')}`}
          </span>
          {lastUpdated && (
            <span className="muted" style={{ fontSize: '0.75rem', marginLeft: '0.5rem' }}>
              {t('lastUpdated', {
                time: formatTime(new Date(lastUpdated).toISOString()),
              })}
            </span>
          )}
        </h2>

        {cancelled ? (
          <p className="notice error">{t('cancelledNotice')}</p>
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
                    <span className="status-name">{ts(status)}</span>
                    {at && <span className="muted status-at">{formatTime(at)}</span>}
                  </span>
                </li>
              );
            })}
          </ol>
        )}
      </section>

      <section>
        <h2 className="section-title">{t('orderSummary')}</h2>
        <ul className="order-items">
          {order.items.map((item, idx) => (
            <li key={idx} className="order-item-row">
              <span>
                {item.productName}{' '}
                <span className="muted">
                  {t('itemLine', { label: item.label, qty: item.qty })}
                </span>
              </span>
              <span>{formatRupees(item.lineTotal)}</span>
            </li>
          ))}
        </ul>
        <div className="cart-summary">
          <div className="cart-summary-row">
            <span>{t('subtotal')}</span>
            <span>{formatRupees(order.subtotal)}</span>
          </div>
          <div className="cart-summary-row total">
            <span>{t('total')}</span>
            <strong>{formatRupees(order.total)}</strong>
          </div>
          <div className="cart-summary-row">
            <span>{t('payment')}</span>
            <span>
              {order.paymentMethod === 'COD' ? tCheckout('cod') : 'UPI'} ·{' '}
              {order.paymentStatus}
            </span>
          </div>
        </div>
      </section>

      <section className="order-address">
        <h2 className="section-title">{t('deliveringTo')}</h2>
        <p>
          <strong>{order.customerName}</strong> · {order.phone}
          <br />
          {order.address.line}
        </p>
      </section>

      <Link href="/" className="btn btn-outline btn-block">
        {tc('continueShopping')}
      </Link>
    </>
  );
}
