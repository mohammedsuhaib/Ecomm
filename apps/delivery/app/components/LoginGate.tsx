'use client';

import { useState, type FormEvent, type ReactNode } from 'react';
import { ApiError } from '@/app/lib/api';
import { useAuth } from './AuthProvider';

export default function LoginGate({ children }: { children: ReactNode }) {
  const { isAuthenticated, ready, login } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login(email.trim(), password);
    } catch (err) {
      if (err instanceof Error && err.message.includes('delivery access')) {
        setError('This account is not set up for delivery. Contact your manager.');
      } else if (err instanceof ApiError && err.status === 401) {
        setError('Incorrect email or password.');
      } else if (err instanceof ApiError && err.status === 403) {
        // The password was right; an admin has switched this account off. Say
        // so — "wrong password" would send the rider to a reset that can't help.
        setError('Your account has been deactivated. Please contact the store manager.');
      } else if (err instanceof ApiError && err.status === 429) {
        // Failed passwords are throttled per account: waiting is the fix, so
        // don't tell a rider to try again straight away.
        setError('Too many failed attempts. Wait a few minutes and try again.');
      } else if (err instanceof ApiError && err.status === 0) {
        setError('Cannot reach server. Check your connection.');
      } else {
        setError('Sign-in failed. Please try again.');
      }
    } finally {
      setBusy(false);
    }
  }

  if (!ready) return null;
  if (isAuthenticated) return <>{children}</>;

  return (
    <div className="login-wrap">
      <form className="login-card" onSubmit={onSubmit}>
        <div className="login-brand">
          <span className="login-logo" aria-hidden>🛵</span>
          <span>Town Basket Delivery</span>
        </div>
        <p className="login-sub">Sign in with your delivery account.</p>

        <label className="field" htmlFor="email">
          Email
          <input
            id="email"
            type="email"
            autoComplete="username"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
            autoFocus
          />
        </label>

        <label className="field" htmlFor="password">
          Password
          <input
            id="password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </label>

        {error && <p className="field-error">{error}</p>}

        <button
          type="submit"
          className="btn btn-primary"
          disabled={busy || !email.trim() || !password}
        >
          {busy ? 'Signing in…' : 'Sign in'}
        </button>

    </div>
  );
}
