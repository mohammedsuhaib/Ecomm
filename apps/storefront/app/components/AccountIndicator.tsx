'use client';

import Link from 'next/link';
import { useAuth } from './AuthProvider';

/**
 * Header account link. Shows "Login" when signed out and the user's name (or
 * "Account") when signed in. Mirrors the CartIndicator pill styling.
 */
export default function AccountIndicator() {
  const { user, isAuthenticated } = useAuth();
  const label = isAuthenticated
    ? user?.name?.trim() || 'Account'
    : 'Login';
  return (
    <Link
      href={isAuthenticated ? '/account' : '/account/login'}
      className="account-pill"
      aria-label={isAuthenticated ? 'Your account' : 'Log in'}
    >
      <span aria-hidden>👤</span>
      <span className="account-label">{label}</span>
    </Link>
  );
}
