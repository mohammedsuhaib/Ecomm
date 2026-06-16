'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { adminOrderStreamUrl, getAdminOrders } from '@/app/lib/api';
import { STATUS_TABS } from '@/app/lib/status';
import type { Order } from '@/app/lib/types';
import OrderCard from './OrderCard';

/**
 * Live admin order queue. Loads orders for the selected status filter, then
 * subscribes to the admin SSE stream for new orders + transitions; on any
 * event it refetches the current filter so the list stays canonical. Falls
 * back to polling if EventSource is unavailable or erroring (ARCHITECTURE §3.8).
 * Mobile/tablet-friendly card layout.
 */
export default function OrderQueue() {
  const [status, setStatus] = useState('');
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [live, setLive] = useState(false);

  // Keep the latest filter in a ref so the SSE/polling handlers refetch the
  // right slice without re-subscribing on every filter change.
  const statusRef = useRef(status);
  statusRef.current = status;

  const load = useCallback(async (filter: string) => {
    setLoading(true);
    setError(null);
    try {
      const pageData = await getAdminOrders(filter || undefined);
      setOrders(pageData.content);
    } catch {
      setError('Could not load orders. Check the connection and retry.');
    } finally {
      setLoading(false);
    }
  }, []);

  // Reload when the filter changes.
  useEffect(() => {
    void load(status);
  }, [status, load]);

  // Subscribe once; refetch the current filter on each event.
  useEffect(() => {
    let pollTimer: ReturnType<typeof setInterval> | null = null;
    let es: EventSource | null = null;

    const refetch = () => {
      getAdminOrders(statusRef.current || undefined)
        .then((p) => setOrders(p.content))
        .catch(() => {
          /* transient */
        });
    };

    const startPolling = () => {
      if (pollTimer) return;
      pollTimer = setInterval(refetch, 8000);
    };

    if (typeof EventSource !== 'undefined') {
      try {
        es = new EventSource(adminOrderStreamUrl());
        es.onopen = () => setLive(true);
        es.onmessage = () => refetch();
        es.onerror = () => {
          setLive(false);
          startPolling();
        };
      } catch {
        startPolling();
      }
    } else {
      startPolling();
    }

    return () => {
      if (es) es.close();
      if (pollTimer) clearInterval(pollTimer);
    };
  }, []);

  // Patch a single order in place after a transition (avoids a full reload).
  const onUpdated = useCallback(
    (updated: Order) => {
      setOrders((prev) => {
        const filter = statusRef.current;
        const mapped = prev.map((o) => (o.id === updated.id ? updated : o));
        // If a filter is active and the order no longer matches, drop it.
        if (filter && updated.status !== filter) {
          return mapped.filter((o) => o.id !== updated.id);
        }
        return mapped;
      });
    },
    [],
  );

  return (
    <section className="queue">
      <div className="queue-tabs" role="tablist" aria-label="Order status filter">
        {STATUS_TABS.map((tab) => (
          <button
            key={tab.value || 'all'}
            type="button"
            role="tab"
            aria-selected={status === tab.value}
            className={`queue-tab ${status === tab.value ? 'active' : ''}`}
            onClick={() => setStatus(tab.value)}
          >
            {tab.label}
          </button>
        ))}
        <span className={`live-dot ${live ? 'on' : ''}`}>
          {live ? '● live' : '○ reconnecting'}
        </span>
      </div>

      {error && <p className="order-error queue-error">{error}</p>}

      {loading && orders.length === 0 ? (
        <p className="queue-empty">Loading orders…</p>
      ) : orders.length === 0 ? (
        <p className="queue-empty">No orders here right now.</p>
      ) : (
        <div className="order-grid">
          {orders.map((order) => (
            <OrderCard key={order.id} order={order} onUpdated={onUpdated} />
          ))}
        </div>
      )}
    </section>
  );
}
