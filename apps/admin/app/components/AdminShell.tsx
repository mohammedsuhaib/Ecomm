'use client';

import { useState, type ReactNode } from 'react';
import { useAuth } from './AuthProvider';
import ChangePassword from './ChangePassword';
import LoginGate from './LoginGate';

/**
 * Authenticated admin shell: header (brand + signed-in staff email + Logout)
 * and the gated main content. The header's user controls only render once a
 * staffer is signed in; the whole page body is wrapped in <LoginGate>.
 */
export default function AdminShell({ children }: { children: ReactNode }) {
  const { user, isAuthenticated, logout } = useAuth();
  const [signingOut, setSigningOut] = useState(false);

  async function onLogout() {
    setSigningOut(true);
    try {
      await logout();
    } finally {
      setSigningOut(false);
    }
  }

  return (
    <>
      <header className="admin-header">
        <div className="admin-header-inner">
          <span className="admin-brand">
            <span className="logo" aria-hidden>
              🧺
            </span>
            Town Basket — Store Admin
          </span>
          {isAuthenticated ? (
            <span className="admin-user">
              <span className="admin-user-email" title={user?.email ?? undefined}>
                {user?.email ?? user?.name ?? 'Signed in'}
              </span>
              <ChangePassword />
              <button
                type="button"
                className="btn btn-ghost admin-logout"
                onClick={onLogout}
                disabled={signingOut}
              >
                {signingOut ? 'Signing out…' : 'Logout'}
              </button>
            </span>
          ) : (
            <span className="admin-sub">Order queue</span>
          )}
        </div>
      </header>
      <main className="admin-main">
        <LoginGate>
          {children}
        </LoginGate>
      </main>
    </>
  );
}
