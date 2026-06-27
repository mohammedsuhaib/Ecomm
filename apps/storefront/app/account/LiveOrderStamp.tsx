'use client';

import { useTranslations } from 'next-intl';
import { useCallback, useEffect, useRef, useState } from 'react';
import { getOrder, orderStreamUrl } from '@/app/lib/api';
import type { Order, OrderStatus } from '@/app/lib/types';

const TERMINAL: ReadonlySet<OrderStatus> = new Set<OrderStatus>([
  'DELIVERED',
  'CANCELLED',
]);

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

/**
 * Live status "stamps" for a single recent order (F6). Renders the current
 * status plus key timestamps (placed; the live status as it advances; the
 * delivered time once delivered). Only IN-FLIGHT orders subscribe to live
 * updates — terminal orders (delivered/cancelled) render statically so we don't
 * hammer the API. Reuses the per-order SSE stream (orderStreamUrl) with a
 * polling fallback, mirroring the full tracking page.
 */
export default function LiveOrderStamp({ order }: { order: Order }) {
  const ts = useTranslations('orderStatus');
  const t = useTranslations('orderStamp');
  const [current, setCurrent] = useState<Order>(order);

  // Keep the latest server-provided order if the parent list reloads.
  useEffect(() => {
    setCurrent(order);
  }, [order]);

  const apply = useCallback((next: Order) => setCurrent(next), []);

  const inFlight = !TERMINAL.has(current.status);
  // The SSE stream is keyed by the numeric order id; the order is re-fetched by
  // its unguessable tracking token (the /orders/track/{token} endpoint — the
  // numeric id is NOT a valid token and would 404).
  const orderId = current.id;
  const trackingToken = current.trackingToken;

  // Subscribe to live updates ONLY while the order is in flight.
  // We re-run this effect when the order leaves the in-flight set so the
  // stream/poll is torn down promptly once it reaches a terminal status.
  const liveRef = useRef(inFlight);
  liveRef.current = inFlight;

  useEffect(() => {
    if (!inFlight) return;
    let pollTimer: ReturnType<typeof setInterval> | null = null;
    let es: EventSource | null = null;

    const refetch = () => {
      getOrder(trackingToken)
        .then(apply)
        .catch(() => {
          /* transient; keep trying */
        });
    };

    // Poll on a gentle cadence regardless of SSE: the stream sends NAMED "status"
    // events (es.onmessage won't receive them) and can stay open+silent, so the
    // poll is what reliably advances the stamp. SSE just makes it snappier.
    pollTimer = setInterval(refetch, 15000);

    if (typeof EventSource !== 'undefined') {
      try {
        es = new EventSource(orderStreamUrl(orderId));
        es.addEventListener('status', refetch);
        es.onmessage = refetch;
      } catch {
        /* polling covers it */
      }
    }

    return () => {
      if (es) es.close();
      if (pollTimer) clearInterval(pollTimer);
    };
  }, [inFlight, orderId, trackingToken, apply]);

  const cancelled = current.status === 'CANCELLED';
  const delivered = current.status === 'DELIVERED';
  // Timeline entry for the current status (the moment it was reached).
  const currentAt = [...current.timeline]
    .reverse()
    .find((t) => t.toStatus === current.status)?.at;

  return (
    <span className="order-stamp">
      <span
        className={`order-stamp-status${cancelled ? ' cancelled' : ''}${
          delivered ? ' delivered' : ''
        }${inFlight ? ' live' : ''}`}
      >
        {inFlight && (
          <span className="order-stamp-dot" aria-hidden>
            ●
          </span>
        )}
        {ts(current.status)}
        {currentAt && current.status !== 'PLACED' && (
          <span className="muted"> · {formatTime(currentAt)}</span>
        )}
      </span>
      <span className="muted order-stamp-placed">
        {t('placed', { time: formatTime(current.placedAt) })}
      </span>
    </span>
  );
}
