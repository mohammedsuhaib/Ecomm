'use client';

import { useState, type FormEvent, type ReactNode } from 'react';
import { ApiError } from '@/app/lib/api';
import { useAuth } from './AuthProvider';

/**
 * Gates the admin app behind staff/admin email+password login (contract §2).
 * Until the auth context has hydrated from localStorage we render nothing
 * (avoids a login-form flash for an already-signed-in staffer). Once ready,
 * authenticated users see `children`; everyone else sees the login form.
 *
 * Dev credentials hint (contract §8): admin@townbasket.local / Admin@12345.
 */
export default function LoginGate({ children }: { children: ReactNode }) {
  const { isAuthenticated, ready, login } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login(email.trim(), password);
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        setError('Incorrect email or password. Please try again.');
      } else if (err instanceof ApiError && err.status === 0) {
        setError('Could not reach the server. Check your connection.');
      } else {
        setError('Sign-in failed. Please try again.');
      }
    } finally {
      setBusy(false);
    }
  }

  // Wait for hydration before deciding what to show.
  if (!ready) return null;

  if (isAuthenticated) return <>{children}</>;

  return (
    <div className="login-wrap">
      <form className="login-card" onSubmit={onSubmit}>
        <h1 className="login-title">
          <span className="logo" aria-hidden>
            🧺
          </span>
          Town Basket Admin
        </h1>
        <p className="login-sub">Sign in with your staff account to continue.</p>

        <label className="login-field" htmlFor="login-email">
          Email
          <input
            id="login-email"
            type="email"
            autoComplete="username"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="admin@townbasket.local"
            required
            autoFocus
          />
        </label>

        <label className="login-field" htmlFor="login-password">
          Password
          <input
            id="login-password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="Admin@12345"
            required
          />
        </label>

        {error && <p className="order-error">{error}</p>}

        <button
          type="submit"
          className="btn login-submit"
          disabled={busy || email.trim().length === 0 || password.length === 0}
        >
          {busy ? 'Signing in…' : 'Sign in'}
        </button>

        <p className="login-hint muted">
          Dev login: <code>admin@townbasket.local</code> / <code>Admin@12345</code>
        </p>
      </form>
    </div>
  );
}
