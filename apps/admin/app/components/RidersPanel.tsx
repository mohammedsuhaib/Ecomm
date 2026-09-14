'use client';

import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';
import {
  ApiError,
  AuthRequiredError,
  createDeliveryAgent,
  getDeliveryAgents,
  getDeliveryStats,
  resetUserPassword,
  setDeliveryAgentActive,
} from '@/app/lib/api';
import { formatRupees } from '@/app/lib/format';
import type { AgentDeliveryStat, DeliveryAgent } from '@/app/lib/types';
import { useAuth } from './AuthProvider';
import { ListSkeleton } from './Skeleton';

const MIN_PASSWORD = 8;

/**
 * Amount of one stat row as a safe number. Guards the render against an API
 * that predates the `amount` field (admin rolled out ahead of the api
 * container): undefined would otherwise poison the sums into NaN and the
 * table would show "₹NaN" instead of degrading to zero / the em dash.
 */
function amountOf(r: AgentDeliveryStat): number {
  return Number(r.amount) || 0;
}

/**
 * Total deliveries for one rider, expandable (native <details>) into the
 * per-date counts and order values. Rows arrive newest-date-first from the API.
 */
function RiderDeliveries({ rows }: { rows: AgentDeliveryStat[] }) {
  const total = rows.reduce((sum, r) => sum + r.deliveries, 0);
  if (total === 0) return <span className="muted">0</span>;
  return (
    <details className="rider-stats">
      <summary>{total} total</summary>
      <ul>
        {rows.map((r) => (
          <li key={r.date}>
            <span className="muted">{r.date}</span> × {r.deliveries} · {formatRupees(amountOf(r))}
          </li>
        ))}
      </ul>
    </details>
  );
}

/** Sum of the rider's delivered-order values across all dates. */
function RiderOrderValue({ rows }: { rows: AgentDeliveryStat[] }) {
  const total = rows.reduce((sum, r) => sum + amountOf(r), 0);
  if (total === 0) return <span className="muted">—</span>;
  return <>{formatRupees(total)}</>;
}

/**
 * Delivery-agent (rider) management: list the roster, onboard a new rider
 * (name / email / password — they log in to the delivery app with these), and
 * activate/deactivate. Deactivated riders can't log in and drop out of the
 * order-assignment dropdown.
 */
export default function RidersPanel() {
  const { refresh: refreshAuth } = useAuth();
  const [agents, setAgents] = useState<DeliveryAgent[]>([]);
  const [stats, setStats] = useState<AgentDeliveryStat[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [togglingId, setTogglingId] = useState<number | null>(null);
  const [resettingId, setResettingId] = useState<number | null>(null);

  // Forgotten password: staff set a new one here and hand it over in person.
  // There is no self-service reset — riders have no verified email — so this
  // is THE recovery path. All the rider's sessions are signed out server-side.
  async function onResetPassword(a: DeliveryAgent) {
    const next = window.prompt(
      `New password for ${a.name ?? a.email ?? 'this rider'} (min ${MIN_PASSWORD} characters).\n` +
        'They will be signed out everywhere and must log in again with it.',
      '',
    );
    if (next === null) return;
    if (next.length < MIN_PASSWORD) {
      setError(`Password must be at least ${MIN_PASSWORD} characters.`);
      return;
    }
    setResettingId(a.id);
    setError(null);
    setSuccess(null);
    try {
      await resetUserPassword(a.id, next);
      setSuccess(`Password reset for ${a.name ?? a.email}. Their old sessions are signed out.`);
    } catch (err) {
      if (err instanceof AuthRequiredError) { refreshAuth(); return; }
      setError(err instanceof ApiError && err.status === 403
        ? 'You are not allowed to reset this account.'
        : 'Could not reset the password. Please try again.');
    } finally {
      setResettingId(null);
    }
  }

  // Group once per stats change: the table re-renders on every keystroke of
  // the add-rider form, and per-row filters would rescan O(agents × stats)
  // each time. A single Map also keeps the Deliveries and Order value cells
  // reading the exact same row set.
  const statsByAgent = useMemo(() => {
    const byAgent = new Map<number, AgentDeliveryStat[]>();
    for (const s of stats) {
      const rows = byAgent.get(s.agentId);
      if (rows) rows.push(s);
      else byAgent.set(s.agentId, [s]);
    }
    return byAgent;
  }, [stats]);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [roster, deliveryStats] = await Promise.all([
        getDeliveryAgents(true),
        getDeliveryStats(),
      ]);
      setAgents(roster);
      setStats(deliveryStats);
    } catch (err) {
      if (err instanceof AuthRequiredError) refreshAuth();
      else setError('Could not load riders.');
    } finally {
      setLoading(false);
    }
  }, [refreshAuth]);

  useEffect(() => {
    void load();
  }, [load]);

  const canSubmit =
    !busy &&
    name.trim().length > 0 &&
    email.trim().includes('@') &&
    password.length >= MIN_PASSWORD;

  async function onCreate(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!canSubmit) return;
    setBusy(true);
    setFormError(null);
    setSuccess(null);
    try {
      const created = await createDeliveryAgent({
        name: name.trim(),
        email: email.trim(),
        password,
      });
      setName('');
      setEmail('');
      setPassword('');
      setSuccess(`Rider "${created.name ?? created.email}" added.`);
      await load();
    } catch (err) {
      if (err instanceof AuthRequiredError) {
        refreshAuth();
      } else if (err instanceof ApiError && err.status === 422) {
        setFormError('That email is already in use.');
      } else if (err instanceof ApiError && err.status === 400) {
        setFormError('Check the details — name, a valid email, and an 8+ char password.');
      } else {
        setFormError('Could not add the rider. Please try again.');
      }
    } finally {
      setBusy(false);
    }
  }

  async function onToggle(agent: DeliveryAgent) {
    setTogglingId(agent.id);
    setError(null);
    try {
      const updated = await setDeliveryAgentActive(agent.id, !agent.active);
      setAgents((prev) => prev.map((a) => (a.id === updated.id ? updated : a)));
    } catch (err) {
      if (err instanceof AuthRequiredError) refreshAuth();
      else setError('Could not update the rider. Please try again.');
    } finally {
      setTogglingId(null);
    }
  }

  return (
    <section className="inv-panel">
      <div className="inv-panel-head">
        <h2 className="cat-panel-title">Riders</h2>
        <span className="muted" style={{ fontSize: '0.85rem' }}>
          {agents.length} total
        </span>
      </div>

      <form className="rider-form" onSubmit={onCreate}>
        <label className="login-field">
          Name
          <input
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="Ravi Kumar"
            autoComplete="off"
          />
        </label>
        <label className="login-field">
          Email
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="ravi@townbasket.local"
            autoComplete="off"
          />
        </label>
        <label className="login-field">
          Password
          <input
            type="text"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="min 8 characters"
            autoComplete="off"
          />
        </label>
        <button type="submit" className="btn" disabled={!canSubmit}>
          {busy ? 'Adding…' : 'Add rider'}
        </button>
      </form>

      {formError && <p className="order-error">{formError}</p>}
      {success && <p className="account-banner ok">{success}</p>}
      {error && <p className="order-error">{error}</p>}

      {loading && agents.length === 0 ? (
        <ListSkeleton label="Loading riders…" rows={4} />
      ) : agents.length === 0 ? (
        <p className="queue-empty">No riders yet. Add one above.</p>
      ) : (
        <div className="prod-table-wrap">
          <table className="prod-table rider-table">
            <thead>
              <tr>
                <th>Name</th>
                <th>Email</th>
                <th>Status</th>
                <th>Deliveries</th>
                <th>Order value</th>
                <th className="actions-col">Action</th>
              </tr>
            </thead>
            <tbody>
              {agents.map((a) => {
                const riderStats = statsByAgent.get(a.id) ?? [];
                return (
                <tr key={a.id} className={a.active ? '' : 'rider-inactive'}>
                  <td>{a.name ?? '—'}</td>
                  <td className="muted">{a.email ?? '—'}</td>
                  <td>
                    <span className={`rider-badge ${a.active ? 'on' : 'off'}`}>
                      {a.active ? 'Active' : 'Inactive'}
                    </span>
                    {a.active && a.onDuty === false && (
                      <span className="rider-badge duty-off" title="The rider switched themselves off duty; no new assignments">
                        Off duty
                      </span>
                    )}
                  </td>
                  <td>
                    <RiderDeliveries rows={riderStats} />
                  </td>
                  <td>
                    <RiderOrderValue rows={riderStats} />
                  </td>
                  <td className="actions-col">
                    <button
                      type="button"
                      className="btn btn-ghost"
                      style={{ fontSize: '0.82rem', padding: '0.3rem 0.7rem' }}
                      disabled={togglingId === a.id}
                      onClick={() => onToggle(a)}
                    >
                      {togglingId === a.id
                        ? '…'
                        : a.active
                          ? 'Deactivate'
                          : 'Activate'}
                    </button>
                    <button
                      type="button"
                      className="btn btn-ghost"
                      style={{ fontSize: '0.82rem', padding: '0.3rem 0.7rem' }}
                      disabled={resettingId === a.id}
                      onClick={() => onResetPassword(a)}
                    >
                      {resettingId === a.id ? '…' : 'Reset password'}
                    </button>
                  </td>
                </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
