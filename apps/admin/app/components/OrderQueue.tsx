'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import {
  adminOrderStreamUrl,
  AuthRequiredError,
  getAdminOrders,
  getDeliveryAgents,
} from '@/app/lib/api';
import { STATUS_TABS } from '@/app/lib/status';
import type { DeliveryAgent, Order } from '@/app/lib/types';
import { useAuth } from './AuthProvider';
import OrderCard from './OrderCard';
import { useNewOrderAlert } from './useNewOrderAlert';

/**
 * Live admin order queue. Loads orders for the selected status filter, then
 * subscribes to the admin SSE stream for new orders + transitions; on any
 * event it refetches the current filter so the list stays canonical. Falls
 * back to polling if EventSource is unavailable or erroring (ARCHITECTURE §3.8).
 * Mobile/tablet-friendly card layout.
 */
export default function OrderQueue() {
  const { refresh: refreshAuth } = useAuth();
  const [status, setStatus] = useState('');
  // Free-text search: order no., phone or name. Debounced before it hits the
  // API; kept in a ref (like the status) so the live refetch searches the
  // same thing the user is looking at.
  const [q, setQ] = useState('');
  const [debouncedQ, setDebouncedQ] = useState('');
  const [orders, setOrders] = useState<Order[]>([]);
  const [agents, setAgents] = useState<DeliveryAgent[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [live, setLive] = useState(false);

  // Keep the latest filter in a ref so the SSE/polling handlers refetch the
  // right slice without re-subscribing on every filter change.
  const statusRef = useRef(status);
  statusRef.current = status;
  const qRef = useRef(debouncedQ);
  qRef.current = debouncedQ;

  useEffect(() => {
    const id = setTimeout(() => setDebouncedQ(q.trim()), 300);
    return () => clearTimeout(id);
  }, [q]);

  // Chime + desktop notification when an order arrives. Held in a ref for the
  // same reason as the filter: the SSE effect must not re-subscribe every time
  // the alert toggle changes.
  const alert = useNewOrderAlert();
  const notifyRef = useRef(alert.notify);
  notifyRef.current = alert.notify;

  // When a call hits an unrecoverable 401, api.ts has already cleared the
  // stored session; re-sync the auth context so <LoginGate> drops to the login
  // form (the queue then unmounts). Show a clear message on the way out.
  const onAuthExpired = useCallback(() => {
    setError('Session expired — please log in again.');
    setLive(false);
    refreshAuth();
  }, [refreshAuth]);

  const load = useCallback(
    async (filter: string, search: string) => {
      setLoading(true);
      setError(null);
      try {
        const pageData = await getAdminOrders(filter || undefined, search || undefined);
        setOrders(pageData.content);
      } catch (err) {
        if (err instanceof AuthRequiredError) {
          onAuthExpired();
        } else {
          setError('Could not load orders. Check the connection and retry.');
        }
      } finally {
        setLoading(false);
      }
    },
    [onAuthExpired],
  );

  // Reload when the filter or the (debounced) search changes.
  useEffect(() => {
    void load(status, debouncedQ);
  }, [status, debouncedQ, load]);

  // Load the delivery-agent roster once (for the assignment dropdown). Best-effort:
  // a failure just leaves assignment disabled, it doesn't break the queue.
  useEffect(() => {
    getDeliveryAgents()
      .then(setAgents)
      .catch(() => {
        /* non-fatal — dropdown stays empty */
      });
  }, []);

  // Subscribe once; refetch the current filter on each event. The SSE URL now
  // carries the access token as a `?token=` query param (contract §6) since
  // EventSource cannot send an Authorization header.
  useEffect(() => {
    let pollTimer: ReturnType<typeof setInterval> | null = null;
    let es: EventSource | null = null;

    const refetch = () => {
      getAdminOrders(statusRef.current || undefined, qRef.current || undefined)
        .then((p) => setOrders(p.content))
        .catch((err) => {
          // A refresh-exhausted 401 means the session is gone — force re-login.
          if (err instanceof AuthRequiredError) {
            if (es) es.close();
            if (pollTimer) clearInterval(pollTimer);
            onAuthExpired();
          }
          /* otherwise transient — leave the current list in place */
        });
    };

    // Poll on a steady cadence regardless of SSE: the admin stream emits NAMED
    // events ("order-placed"/"order-updated"), which es.onmessage doesn't receive,
    // and the connection can stay open+silent — so the poll keeps the queue current
    // (and reliably surfaces a dead token via AuthRequiredError). SSE just makes
    // new orders / transitions appear instantly when it works.
    pollTimer = setInterval(refetch, 8000);

    if (typeof EventSource !== 'undefined') {
      try {
        es = new EventSource(adminOrderStreamUrl());
        es.onopen = () => setLive(true);
        // Only a genuinely new order alerts; status transitions must not, or a
        // busy shift becomes a wall of noise.
        es.addEventListener('order-placed', () => {
          notifyRef.current('A new order just came in.');
          refetch();
        });
        es.addEventListener('order-updated', () => refetch());
        es.onmessage = () => refetch();
        es.onerror = () => setLive(false);
      } catch {
        /* polling covers it */
      }
    }

    return () => {
      if (es) es.close();
      if (pollTimer) clearInterval(pollTimer);
    };
  }, [onAuthExpired]);

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
      <div className="queue-search">
        <input
          type="search"
          className="queue-search-input"
          placeholder="Search order code, phone or name…"
          aria-label="Search orders by order code, phone or customer name"
          value={q}
          onChange={(e) => setQ(e.target.value)}
        />
        {debouncedQ && (
          <span className="muted queue-search-hint">
            {orders.length === 0 && !loading
              ? `No orders match “${debouncedQ}”`
              : `${orders.length} match${orders.length === 1 ? '' : 'es'}`}
          </span>
        )}
      </div>
      {/* aria-pressed toggles, not a half-implemented ARIA tabs pattern. */}
      <div className="queue-tabs" aria-label="Order status filter">
        {STATUS_TABS.map((tab) => (
          <button
            key={tab.value || 'all'}
            type="button"
            aria-pressed={status === tab.value}
            className={`queue-tab ${status === tab.value ? 'active' : ''}`}
            onClick={() => setStatus(tab.value)}
          >
            {tab.label}
          </button>
        ))}
        <span className={`live-dot ${live ? 'on' : ''}`}>
          {live ? '● live' : '○ reconnecting'}
        </span>
        <button
          type="button"
          className={`queue-alert-toggle ${alert.enabled ? 'on' : ''}`}
          aria-pressed={alert.enabled}
          onClick={() => void alert.toggle()}
          title={
            alert.enabled
              ? 'New-order alerts are on for this browser'
              : 'Play a sound and show a notification when a new order arrives'
          }
        >
          {alert.enabled ? '🔔 Alerts on' : '🔕 Alerts off'}
        </button>
      </div>

      {error && <p className="order-error queue-error">{error}</p>}

      {loading && orders.length === 0 ? (
        <p className="queue-empty">Loading orders…</p>
      ) : orders.length === 0 ? (
        <p className="queue-empty">No orders here right now.</p>
      ) : (
        <div className="order-grid">
          {orders.map((order) => (
            <OrderCard
              key={order.id}
              order={order}
              agents={agents}
              onUpdated={onUpdated}
            />
          ))}
        </div>
      )}
    </section>
  );
}
