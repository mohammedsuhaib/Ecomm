'use client';

import { useCallback, useEffect, useState } from 'react';
import { AuthRequiredError, getDeliveryOrders, getDeliverySummary } from '@/app/lib/api';
import type { DaySummary, Order } from '@/app/lib/types';
import { useAuth } from './AuthProvider';

const PAGE_SIZE = 50;

function fmtAmount(n: number) {
  return '₹' + n.toLocaleString('en-IN');
}

/** "14:32" for today, "15 Sep, 14:32" for anything older. */
function fmtDelivered(iso: string) {
  const d = new Date(iso);
  const now = new Date();
  const sameDay = d.toDateString() === now.toDateString();
  const time = d.toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' });
  return sameDay ? time : `${d.toLocaleDateString('en-IN', { day: 'numeric', month: 'short' })}, ${time}`;
}

/** "Mon, 15 Sep" from the server's ISO date — the STORE day, not the phone's. */
function fmtSummaryDate(isoDate: string) {
  const [y, m, d] = isoDate.split('-').map(Number);
  return new Date(y, m - 1, d).toLocaleDateString('en-IN', { weekday: 'short', day: 'numeric', month: 'short' });
}

/** The handover moment: the last DELIVERED step. Falls back to placed-at, which every order has. */
function deliveredAt(order: Order): string {
  for (let i = order.timeline.length - 1; i >= 0; i--) {
    if (order.timeline[i].toStatus === 'DELIVERED') return order.timeline[i].at;
  }
  return order.placedAt;
}

/**
 * The rider's own record: what they delivered and what cash they took at the
 * door. Read-only — a delivered order has nothing left to do. The tally is
 * today's (the store day); the list is everything they have ever delivered,
 * newest first, each card dated so the two never get confused.
 */
export default function CompletedDeliveries() {
  const { refresh } = useAuth();
  const [summary, setSummary] = useState<DaySummary | null>(null);
  const [orders, setOrders] = useState<Order[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      // Both at once: the strip and the list are one answer to one question.
      const [day, page] = await Promise.all([
        getDeliverySummary(),
        getDeliveryOrders('DELIVERED', 0, PAGE_SIZE),
      ]);
      setSummary(day);
      setOrders(page.content);
      setTotal(page.totalElements);
      setError(null);
    } catch (err) {
      if (err instanceof AuthRequiredError) { refresh(); return; }
      setError('Could not load your completed deliveries. Check your connection and refresh.');
    } finally {
      setLoading(false);
    }
  }, [refresh]);

  useEffect(() => { void load(); }, [load]);

  async function loadMore() {
    if (loadingMore) return;
    setLoadingMore(true);
    try {
      const next = await getDeliveryOrders('DELIVERED', Math.floor(orders.length / PAGE_SIZE), PAGE_SIZE);
      // Keyed merge: a delivery confirmed between two pages shifts the paging,
      // and the same order must never appear twice.
      setOrders((prev) => {
        const seen = new Set(prev.map((o) => o.id));
        return [...prev, ...next.content.filter((o) => !seen.has(o.id))];
      });
      setTotal(next.totalElements);
    } catch (err) {
      if (err instanceof AuthRequiredError) { refresh(); return; }
      setError('Could not load more. Try again.');
    } finally {
      setLoadingMore(false);
    }
  }

  return (
    <>
      <div className="queue-status">
        {loading ? (
          <span className="queue-updated">Loading…</span>
        ) : (
          <span className="badge-clear">{total} completed</span>
        )}
        <button
          type="button"
          className="btn btn-ghost btn-sm"
          style={{ marginLeft: 'auto' }}
          onClick={() => { setLoading(true); void load(); }}
          disabled={loading}
          aria-label="Refresh completed deliveries"
          aria-busy={loading}
        >
          <span aria-hidden>↻</span> {loading ? 'Refreshing…' : 'Refresh'}
        </button>
      </div>

      {/* Today's tally. Cash is the number the rider settles with the store. */}
      {summary && (
        <section className="day-summary" aria-label="Today's summary">
          <p className="day-summary-title">Today · {fmtSummaryDate(summary.date)}</p>
          <div className="day-stats">
            <div className="day-stat">
              <span className="day-stat-value">{summary.deliveredCount}</span>
              <span className="day-stat-label">delivered</span>
            </div>
            <div className="day-stat day-stat-cash">
              <span className="day-stat-value">{fmtAmount(summary.codCollected)}</span>
              <span className="day-stat-label">
                cash collected
                {summary.codOrders > 0 && ` · ${summary.codOrders} order${summary.codOrders !== 1 ? 's' : ''}`}
              </span>
            </div>
          </div>
          <p className="day-summary-hint">
            Cash counts Pay on Delivery only — UPI orders were paid before you picked them up.
          </p>
        </section>
      )}

      {error && <p className="field-error queue-error">{error}</p>}

      {loading && orders.length === 0 ? (
        <div className="dcard-list" aria-busy="true" aria-label="Loading completed deliveries">
          <div className="skeleton-card" />
          <div className="skeleton-card" />
        </div>
      ) : orders.length === 0 ? (
        <div className="queue-empty-state">
          <div className="queue-empty-icon">📦</div>
          <p>No completed deliveries yet.</p>
          <p className="queue-empty-sub">Orders you confirm with the customer&apos;s OTP will show up here.</p>
        </div>
      ) : (
        <>
          <h2 className="completed-heading">All completed deliveries</h2>
          <div className="dcard-list">
            {orders.map((order) => <CompletedCard key={order.id} order={order} />)}
          </div>
          {orders.length < total && (
            <button
              type="button"
              className="btn btn-ghost load-more"
              onClick={loadMore}
              disabled={loadingMore}
            >
              {loadingMore ? 'Loading…' : `Show more (${total - orders.length} older)`}
            </button>
          )}
        </>
      )}
    </>
  );
}

function CompletedCard({ order }: { order: Order }) {
  const [expanded, setExpanded] = useState(false);
  const isCod = order.paymentMethod === 'COD';

  return (
    <div className="dcard dcard-completed">
      <div className="dcard-head">
        <span className="dcard-id">#{order.publicCode}</span>
        <span className="dcard-time dcard-delivered-at">
          <span aria-hidden>✓</span> Delivered {fmtDelivered(deliveredAt(order))}
        </span>
        <span className={`dcard-pay ${isCod ? 'cod' : 'upi'}`}>
          {isCod ? `Collected ${fmtAmount(order.total)}` : 'UPI Paid'}
        </span>
      </div>

      <div className="dcard-customer">
        <h3 className="dcard-name">{order.customerName}</h3>
      </div>

      <div className="dcard-address">
        <span className="dcard-addr-text">{order.address.line}</span>
      </div>

      <button
        type="button"
        className="dcard-items-toggle"
        onClick={() => setExpanded((v) => !v)}
        aria-expanded={expanded}
        aria-controls={`done-items-${order.id}`}
      >
        {order.items.length} item{order.items.length !== 1 ? 's' : ''} · {fmtAmount(order.total)}
        <span className="dcard-chevron" aria-hidden>{expanded ? '▲' : '▼'}</span>
      </button>

      {expanded && (
        <ul className="dcard-items" id={`done-items-${order.id}`}>
          {order.items.map((item, i) => (
            <li key={i} className="dcard-item">
              <span>{item.productName} <span className="dcard-item-label">{item.label}</span> × {item.qty}</span>
              <span>{fmtAmount(item.lineTotal)}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
