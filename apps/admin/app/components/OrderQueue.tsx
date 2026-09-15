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
import { ListSkeleton } from './Skeleton';

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
  // Three honest states, not two. `live` alone started false and was rendered
  // as "reconnecting", so the queue claimed to be reconnecting before it had
  // ever connected — and kept claiming it when the stream was down but the 8s
  // poll was keeping the list perfectly current. Mirrors the storefront
  // tracking page's connecting/live/polling indicator.
  const [conn, setConn] = useState<'connecting' | 'live' | 'polling'>('connecting');

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
    setConn('polling');
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

  // The delivery-agent roster for the assignment dropdown. Best-effort: a
  // failure just leaves assignment disabled, it doesn't break the queue.
  const loadAgents = useCallback(() => {
    getDeliveryAgents()
      .then(setAgents)
      .catch(() => {
        /* non-fatal — dropdown stays empty */
      });
  }, []);

  // Once on mount, and again whenever the server rejects an assignment: the
  // list is active riders as of page load, so a rider deactivated since is
  // still offered. Re-reading it on rejection takes them out of the dropdown
  // instead of leaving staff to pick the same dead name again.
  useEffect(() => {
    loadAgents();
  }, [loadAgents]);

  // Subscribe once; refetch the current filter on each event. The SSE URL now
  // carries the access token as a `?token=` query param (contract §6) since
  // EventSource cannot send an Authorization header.
  useEffect(() => {
    let pollTimer: ReturnType<typeof setInterval> | null = null;
    let es: EventSource | null = null;
    let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
    let torndown = false;
    let attempt = 0;

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

    /**
     * Alert on the transitions staff actually need to look up for. A new order
     * is the loud one; a cancellation and a delivery are the two that change
     * what the team should be doing next. Every other step (CONFIRMED, PACKING,
     * …) is staff's own action coming back to them, so it stays silent — that
     * is what keeps a busy shift from becoming a wall of noise.
     */
    const alertFor = (event: MessageEvent<string>) => {
      let status: string | undefined;
      try {
        status = (JSON.parse(event.data) as { status?: string }).status;
      } catch {
        // A frame we can't parse is still worth refetching for; just don't
        // guess at an alert for it.
        return;
      }
      if (status === 'CANCELLED') {
        notifyRef.current({
          title: 'Order cancelled',
          body: 'An order was just cancelled.',
          tag: 'tb-order-cancelled',
        });
      } else if (status === 'DELIVERED') {
        notifyRef.current({
          title: 'Order delivered',
          body: 'An order was just delivered.',
          tag: 'tb-order-delivered',
        });
      }
    };

    /**
     * Open the stream, and keep it openable.
     *
     * <p>The URL is rebuilt on every attempt because it carries the access
     * token as `?token=` — EventSource cannot set headers. That token lives 15
     * minutes, and when it expires the server answers the stream request with a
     * 401; per the EventSource spec a non-200 response fails the connection
     * PERMANENTLY, with no automatic retry. So the old code's single connection
     * simply died a quarter of an hour into every shift, the indicator stuck on
     * "reconnecting" forever, and no new-order alert ever fired again — while
     * the poll quietly kept the list correct, which is what made it look like
     * only the alerts were broken. Reconnecting with a freshly read token (the
     * poll's own 401 handling has refreshed it by then) is the fix.
     */
    const connect = () => {
      if (torndown || typeof EventSource === 'undefined') return;
      try {
        es = new EventSource(adminOrderStreamUrl());
      } catch {
        setConn('polling'); // polling covers it
        return;
      }
      es.onopen = () => {
        attempt = 0;
        setConn('live');
      };
      es.addEventListener('order-placed', () => {
        notifyRef.current({
          title: 'New order',
          body: 'A new order just came in.',
          tag: 'tb-new-order',
        });
        refetch();
      });
      es.addEventListener('order-updated', (event) => {
        alertFor(event as MessageEvent<string>);
        refetch();
      });
      es.onmessage = () => refetch();
      es.onerror = () => {
        setConn('polling');
        if (es) {
          es.close();
          es = null;
        }
        if (torndown) return;
        // 5s, 10s, 20s, 40s, then every minute. The poll is already keeping the
        // queue current, so there is nothing to gain by hammering a stream whose
        // token may simply not be valid yet.
        const delay = Math.min(5000 * 2 ** attempt, 60_000);
        attempt += 1;
        reconnectTimer = setTimeout(connect, delay);
      };
    };

    connect();

    return () => {
      torndown = true;
      if (es) es.close();
      if (pollTimer) clearInterval(pollTimer);
      if (reconnectTimer) clearTimeout(reconnectTimer);
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
        {/* "polling" is not a failure state to apologise for: the queue is
            still refreshing every 8 seconds, it just isn't instant. Saying
            "reconnecting" made a working dashboard look broken. */}
        <span
          className={`live-dot ${conn === 'live' ? 'on' : ''}`}
          title={
            conn === 'live'
              ? 'Live: new orders appear the moment they are placed'
              : conn === 'connecting'
                ? 'Opening the live stream…'
                : 'Live stream unavailable — the queue refreshes every 8 seconds instead'
          }
        >
          {conn === 'live'
            ? '● live'
            : conn === 'connecting'
              ? '○ connecting'
              : '○ updating every 8s'}
        </span>
        <button
          type="button"
          className={`queue-alert-toggle ${alert.enabled ? 'on' : ''}`}
          aria-pressed={alert.enabled}
          onClick={() => void alert.toggle()}
          title={
            alert.enabled
              ? 'Alerts are on for this browser — new, cancelled and delivered orders'
              : 'Play a sound and show a notification when an order arrives, is cancelled or is delivered. Per browser: switch it on wherever staff watch the queue.'
          }
        >
          {alert.enabled ? '🔔 Alerts on' : '🔕 Alerts off'}
        </button>
      </div>

      {error && <p className="order-error queue-error">{error}</p>}

      {loading && orders.length === 0 ? (
        <ListSkeleton label="Loading orders…" rows={4} />
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
              onAgentsStale={loadAgents}
            />
          ))}
        </div>
      )}
    </section>
  );
}
