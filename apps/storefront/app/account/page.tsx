'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import { useAuth } from '@/app/components/AuthProvider';
import AddressManager from './AddressManager';
import OrderHistory from './OrderHistory';

export default function AccountPage() {
  const router = useRouter();
  const { user, isAuthenticated, logout, refreshUser } = useAuth();
  // Auth hydrates from localStorage after mount, so wait one tick before
  // deciding to redirect — otherwise a logged-in user gets bounced to login.
  const [checked, setChecked] = useState(false);
  const [loggingOut, setLoggingOut] = useState(false);

  useEffect(() => {
    setChecked(true);
  }, []);

  // Guard: redirect to login once we know there is no session.
  useEffect(() => {
    if (checked && !isAuthenticated) {
      router.replace('/account/login?next=/account');
    }
  }, [checked, isAuthenticated, router]);

  // Refresh the cached profile from the server on mount.
  useEffect(() => {
    if (isAuthenticated) void refreshUser();
  }, [isAuthenticated, refreshUser]);

  async function onLogout() {
    setLoggingOut(true);
    await logout();
    router.replace('/');
  }

  if (!checked || !isAuthenticated || !user) {
    return <p className="empty-state">Loading your account…</p>;
  }

  return (
    <>
      <nav className="breadcrumb">
        <Link href="/">Home</Link> / <span>Account</span>
      </nav>

      <section className="account-section">
        <div className="account-section-head">
          <h1 className="section-title" style={{ margin: 0 }}>
            Your account
          </h1>
          <button
            type="button"
            className="btn btn-outline"
            onClick={onLogout}
            disabled={loggingOut}
          >
            {loggingOut ? 'Logging out…' : 'Log out'}
          </button>
        </div>
        <div className="profile-card">
          <p style={{ margin: 0 }}>
            <strong>{user.name?.trim() || 'Customer'}</strong>
          </p>
          {user.phone && <p className="muted" style={{ margin: 0 }}>+91 {user.phone}</p>}
          {user.email && <p className="muted" style={{ margin: 0 }}>{user.email}</p>}
        </div>
      </section>

      <section className="account-section">
        <h2 className="section-title">Recent orders</h2>
        <OrderHistory />
      </section>

      <AddressManager />
    </>
  );
}
