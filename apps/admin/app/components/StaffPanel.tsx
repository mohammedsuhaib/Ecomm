'use client';

import { useCallback, useEffect, useState } from 'react';
import { ApiError, AuthRequiredError, getStaff, resetUserPassword } from '@/app/lib/api';
import type { StaffMember } from '@/app/lib/types';
import { useAuth } from './AuthProvider';
import { ListSkeleton } from './Skeleton';

const MIN_PASSWORD = 8;

/**
 * Staff and admin accounts, with the one action a locked-out colleague needs:
 * an admin sets them a new password and tells them in person. Creating staff
 * accounts is still not possible here — they come from the seed for now.
 */
export default function StaffPanel() {
  const { user, refresh: refreshAuth } = useAuth();
  const [staff, setStaff] = useState<StaffMember[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [resettingId, setResettingId] = useState<number | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setStaff(await getStaff());
    } catch (err) {
      if (err instanceof AuthRequiredError) { refreshAuth(); return; }
      setError(err instanceof ApiError && err.status === 403
        ? 'Only an admin can see staff accounts.'
        : 'Could not load staff accounts.');
    } finally {
      setLoading(false);
    }
  }, [refreshAuth]);

  useEffect(() => { void load(); }, [load]);

  async function onReset(m: StaffMember) {
    const next = window.prompt(
      `New password for ${m.name ?? m.email} (min ${MIN_PASSWORD} characters).\n` +
        'They will be signed out everywhere and must log in again with it.',
      '',
    );
    if (next === null) return;
    if (next.length < MIN_PASSWORD) {
      setError(`Password must be at least ${MIN_PASSWORD} characters.`);
      return;
    }
    setResettingId(m.id);
    setError(null);
    setSuccess(null);
    try {
      await resetUserPassword(m.id, next);
      setSuccess(`Password reset for ${m.name ?? m.email}. Their old sessions are signed out.`);
    } catch (err) {
      if (err instanceof AuthRequiredError) { refreshAuth(); return; }
      setError(err instanceof ApiError && err.status === 403
        ? 'You are not allowed to reset this account.'
        : 'Could not reset the password. Please try again.');
    } finally {
      setResettingId(null);
    }
  }

  return (
    <section className="inv-panel">
      <div className="inv-panel-head">
        <h2 className="cat-panel-title">Staff</h2>
        <span className="muted" style={{ fontSize: '0.85rem' }}>
          Your own password: use <em>Change password</em> in the header.
        </span>
      </div>

      {error && <p className="order-error">{error}</p>}
      {success && <p className="account-banner ok">{success}</p>}

      {loading && staff.length === 0 ? (
        <ListSkeleton label="Loading staff…" rows={3} />
      ) : staff.length === 0 ? (
        <p className="queue-empty">No staff accounts found.</p>
      ) : (
        <div className="prod-table-wrap">
          <table className="prod-table rider-table">
            <thead>
              <tr>
                <th>Name</th>
                <th>Email</th>
                <th>Role</th>
                <th>Status</th>
                <th className="actions-col">Action</th>
              </tr>
            </thead>
            <tbody>
              {staff.map((m) => {
                const isMe = user?.id === m.id;
                return (
                  <tr key={m.id} className={m.active ? '' : 'rider-inactive'}>
                    <td>{m.name ?? '—'}{isMe ? <span className="muted"> (you)</span> : null}</td>
                    <td className="muted">{m.email ?? '—'}</td>
                    <td>{m.role === 'ADMIN' ? 'Admin' : 'Store staff'}</td>
                    <td>
                      <span className={`rider-badge ${m.active ? 'on' : 'off'}`}>
                        {m.active ? 'Active' : 'Inactive'}
                      </span>
                    </td>
                    <td className="actions-col">
                      {isMe ? (
                        <span className="muted">—</span>
                      ) : (
                        <button
                          type="button"
                          className="btn btn-ghost"
                          style={{ fontSize: '0.82rem', padding: '0.3rem 0.7rem' }}
                          disabled={resettingId === m.id}
                          onClick={() => onReset(m)}
                        >
                          {resettingId === m.id ? '…' : 'Reset password'}
                        </button>
                      )}
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
