'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import {
  AuthRequiredError,
  getDeliveryOrders,
  getDutyStatus,
  setDutyStatus,
  stopSharingLocation,
} from '@/app/lib/api';
import type { Order } from '@/app/lib/types';
import { useAuth } from './AuthProvider';
import CompletedDeliveries from './CompletedDeliveries';
import DeliveryCard from './DeliveryCard';
import LocationSharing from './LocationSharing';
import PushOptIn from './PushOptIn';

const POLL_MS = 30_000;

type View = 'queue' | 'completed';

export default function DeliveryQueue() {
  const { user, logout, refresh } = useAuth();
  // Which half of the app is showing. The pending queue keeps polling in the
  // background either way, so switching back never shows a stale list.
  const [view, setView] = useState<View>('queue');
  // Two live lists, in the order a rider works them: bags waiting at the store
  // to be collected, then the ones already on the bike. They are separate
  // fetches (one status per request) and separate sections, because they ask
  // different things of the rider — go and get it, versus go and deliver it.
  const [ready, setReady] = useState<Order[]>([]);
  const [orders, setOrders] = useState<Order[]>([]);
  // The rider's own availability. null until loaded so the toggle never
  // flashes a wrong state; a failed load leaves it null and the toggle hidden.
  const [onDuty, setOnDuty] = useState<boolean | null>(null);
  const [dutyBusy, setDutyBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);
  const [offline, setOffline] = useState(false);
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    getDutyStatus().then((d) => setOnDuty(d.onDuty)).catch(() => setOnDuty(null));
  }, []);

  async function toggleDuty() {
    if (onDuty === null || dutyBusy) return;
    setDutyBusy(true);
    try {
      const next = await setDutyStatus(!onDuty);
      setOnDuty(next.onDuty);
    } catch {
      /* leave the switch where it was; the next load re-reads the truth */
    } finally {
      setDutyBusy(false);
    }
  }

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
      // Both at once: a rider standing at the counter needs to see the bag
      // they are collecting and the ones they are already carrying together.
      const [waiting, carrying] = await Promise.all([
        getDeliveryOrders('READY_FOR_DELIVERY'),
        getDeliveryOrders('OUT_FOR_DELIVERY'),
      ]);
      setReady(waiting.content);
      setOrders(carrying.content);
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

  // Collected: move the card from "ready" to "out for delivery" straight away,
  // rather than waiting up to 30 s for the next poll to do it. The server has
  // already made the change; this just stops the rider seeing a bag they are
  // holding still listed as waiting for them.
  const onPickedUp = useCallback((picked: Order) => {
    setReady((prev) => prev.filter((o) => o.id !== picked.id));
    setOrders((prev) => (prev.some((o) => o.id === picked.id) ? prev : [picked, ...prev]));
  }, []);

  // A rider, as opposed to an admin in the dispatcher view. Only riders have a
  // position worth sharing (an admin is never assigned an order).
  const isRider = user?.role === 'DELIVERY_AGENT';

  /** Everything still on this rider's plate — to collect plus to deliver. */
  const pending = ready.length + orders.length;

  const handleLogout = async () => {
    if (pollRef.current) clearInterval(pollRef.current);
    // Forget the server-side position WHILE we still hold a token: after
    // logout the DELETE could not be authorised, and the last fix would sit
    // there until it aged out. Best-effort — signing out must never be blocked
    // by a dead zone.
    if (isRider) await stopSharingLocation().catch(() => undefined);
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
            {onDuty !== null && (
              <button
                type="button"
                className={`duty-toggle ${onDuty ? 'on' : 'off'}`}
                onClick={toggleDuty}
                disabled={dutyBusy}
                aria-pressed={onDuty}
                title={onDuty
                  ? 'On duty — you can be assigned new deliveries. Tap to go off duty.'
                  : 'Off duty — no new deliveries will be assigned to you. Tap to go on duty.'}
              >
                <span className="duty-dot" aria-hidden />
                {dutyBusy ? '…' : onDuty ? 'On duty' : 'Off duty'}
              </button>
            )}
            <span className="dheader-agent">{user?.name ?? user?.email ?? 'Agent'}</span>
            <button type="button" className="btn btn-ghost btn-sm" onClick={handleLogout}>
              Logout
            </button>
          </div>
        </div>
      </header>

      {/* Main */}
      <main className="queue-main">
        <div className="view-tabs" role="tablist" aria-label="Deliveries">
          <button
            type="button"
            role="tab"
            className={`view-tab ${view === 'queue' ? 'active' : ''}`}
            aria-selected={view === 'queue'}
            onClick={() => setView('queue')}
          >
            Deliveries
            {pending > 0 && <span className="tab-count">{pending}</span>}
          </button>
          <button
            type="button"
            role="tab"
            className={`view-tab ${view === 'completed' ? 'active' : ''}`}
            aria-selected={view === 'completed'}
            onClick={() => setView('completed')}
          >
            Completed
          </button>
        </div>

        {view === 'completed' ? (
          <CompletedDeliveries />
        ) : (
          <>
            {/* Status bar */}
            <div className="queue-status">
              {pending > 0 ? (
                <span className="badge-pending">{pending} pending</span>
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

            <PushOptIn />

            {/* Live position for the customers being delivered to — only while
                something is actually OUT for delivery, and only for a rider.
                A bag still waiting on the counter has no customer watching a
                map yet (the API hides the position until it is collected), so
                sharing then would be reporting a rider's whereabouts that
                nobody can see. */}
            {isRider && <LocationSharing active={orders.length > 0} />}

            {onDuty === false && (
              <p className="duty-banner" role="status">
                You&apos;re off duty — finish what&apos;s in your queue; nothing new will be assigned
                until you go back on duty.
              </p>
            )}

            {offline && (
              <p className="offline-banner" role="status">
                You&apos;re offline — the queue may be out of date. It will refresh
                automatically when you&apos;re back online.
              </p>
            )}
            {error && <p className="field-error queue-error">{error}</p>}

            {loading && pending === 0 ? (
              <div className="dcard-list" aria-busy="true" aria-label="Loading your deliveries">
                <div className="skeleton-card" />
                <div className="skeleton-card" />
                <div className="skeleton-card" />
              </div>
            ) : pending === 0 ? (
              <div className="queue-empty-state">
                <div className="queue-empty-icon">✅</div>
                <p>No pending deliveries right now.</p>
                <p className="queue-empty-sub">New orders will appear here automatically.</p>
              </div>
            ) : (
              <>
                {/* To collect first: these are the ones holding the shop up. */}
                {ready.length > 0 && (
                  <section className="dcard-group" aria-label="Ready to collect">
                    <h2 className="dcard-group-title">
                      <span aria-hidden>📦</span> Ready to collect
                      <span className="tab-count">{ready.length}</span>
                    </h2>
                    <p className="dcard-group-hint">
                      Packed and waiting at the store. Tap <strong>Picked up</strong> when you
                      take the bag — that&apos;s when the customer starts seeing you on their map.
                    </p>
                    <div className="dcard-list">
                      {ready.map((order) => (
                        <DeliveryCard
                          key={order.id}
                          order={order}
                          onDelivered={onDelivered}
                          onPickedUp={onPickedUp}
                        />
                      ))}
                    </div>
                  </section>
                )}

                {orders.length > 0 && (
                  <section className="dcard-group" aria-label="Out for delivery">
                    {/* The heading only earns its space once there are two
                        groups; with nothing to collect this is the whole
                        screen and the tab above already says what it is. */}
                    {ready.length > 0 && (
                      <h2 className="dcard-group-title">
                        <span aria-hidden>🛵</span> Out for delivery
                        <span className="tab-count">{orders.length}</span>
                      </h2>
                    )}
                    <div className="dcard-list">
                      {orders.map((order) => (
                        <DeliveryCard
                          key={order.id}
                          order={order}
                          onDelivered={onDelivered}
                          onPickedUp={onPickedUp}
                        />
                      ))}
                    </div>
                  </section>
                )}
              </>
            )}
          </>
        )}
      </main>
    </div>
  );
}
