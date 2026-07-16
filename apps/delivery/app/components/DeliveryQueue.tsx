'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { AuthRequiredError, getDeliveryOrders } from '@/app/lib/api';
import type { Order } from '@/app/lib/types';
import { useAuth } from './AuthProvider';
import DeliveryCard from './DeliveryCard';

const POLL_MS = 30_000;

export default function DeliveryQueue() {
  const { user, logout, refresh } = useAuth();
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);
  const [offline, setOffline] = useState(false);
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // Agents move through network dead zones — surface offline state explicitly
  // instead of letting the queue silently go stale.
  useEffect(() => {
    setOffline(!navigator.onLine);
    const onOnline = () => setOffline(false);
    const onOffline = () => setOffline(true);
    window.addEventListener('online', onOnline);
    window.addEventListener('offline', onOffline);
    return () => {
      window.removeEventListener('online', onOnline);
      window.removeEventListener('offline', onOffline);
    };
  }, []);

  const load = useCallback(async () => {
    try {
      const data = await getDeliveryOrders('OUT_FOR_DELIVERY');
      setOrders(data.content);
      setLastUpdated(new Date());
      setError(null);
    } catch (err) {
      if (err instanceof AuthRequiredError) { refresh(); return; }
      setError('Could not load orders. Retrying…');
    } finally {
      setLoading(false);
    }
  }, [refresh]);

  useEffect(() => {
    void load();
    pollRef.current = setInterval(() => void load(), POLL_MS);
    return () => { if (pollRef.current) clearInterval(pollRef.current); };
  }, [load]);

  const onDelivered = useCallback((id: string) => {
    setOrders((prev) => prev.filter((o) => o.id !== id));
  }, []);

  const handleLogout = async () => {
    if (pollRef.current) clearInterval(pollRef.current);
    await logout();
  };

  return (
    <div className="queue-wrap">
      {/* Header */}
      <header className="dheader">
        <div className="dheader-inner">
          <h1 className="dheader-brand">
            <span aria-hidden>🛵</span>
            Town Basket Delivery
          </h1>
          <div className="dheader-right">
            <span className="dheader-agent">{user?.name ?? user?.email ?? 'Agent'}</span>
            <button type="button" className="btn btn-ghost btn-sm" onClick={handleLogout}>
              Logout
            </button>
          </div>
        </div>
      </header>

      {/* Main */}
      <main className="queue-main">
        {/* Status bar */}
        <div className="queue-status">
          {orders.length > 0 ? (
            <span className="badge-pending">{orders.length} pending</span>
          ) : (
            <span className="badge-clear">All clear</span>
          )}
          <span className="queue-updated">
            {lastUpdated
              ? `Updated ${lastUpdated.toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' })}`
              : 'Loading…'}
          </span>
          <button
            type="button"
            className="btn btn-ghost btn-sm"
            onClick={() => { setLoading(true); void load(); }}
            disabled={loading}
            aria-label="Refresh deliveries"
            aria-busy={loading}
          >
            <span aria-hidden>↻</span> {loading ? 'Refreshing…' : 'Refresh'}
          </button>
        </div>

        {offline && (
          <p className="offline-banner" role="status">
            You&apos;re offline — the queue may be out of date. It will refresh
            automatically when you&apos;re back online.
          </p>
        )}
        {error && <p className="field-error queue-error">{error}</p>}

        {loading && orders.length === 0 ? (
          <div className="dcard-list" aria-busy="true" aria-label="Loading your deliveries">
            <div className="skeleton-card" />
            <div className="skeleton-card" />
            <div className="skeleton-card" />
          </div>
        ) : orders.length === 0 ? (
          <div className="queue-empty-state">
            <div className="queue-empty-icon">✅</div>
            <p>No pending deliveries right now.</p>
            <p className="queue-empty-sub">New orders will appear here automatically.</p>
          </div>
        ) : (
          <div className="dcard-list">
            {orders.map((order) => (
              <DeliveryCard key={order.id} order={order} onDelivered={onDelivered} />
            ))}
          </div>
        )}
      </main>
    </div>
  );
}
