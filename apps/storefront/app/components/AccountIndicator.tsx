'use client';

import Link from 'next/link';
import { useTranslations } from 'next-intl';
import { useAuth } from './AuthProvider';

/**
 * Header account link. Shows "Login" when signed out and the user's name (or
 * "Account") when signed in. Mirrors the CartIndicator pill styling.
 */
export default function AccountIndicator() {
  const { user, isAuthenticated } = useAuth();
  const t = useTranslations('accountIndicator');
  const tc = useTranslations('common');
  const label = isAuthenticated
    ? user?.name?.trim() || tc('account')
    : tc('login');
  return (
    <Link
      href={isAuthenticated ? '/account' : '/account/login'}
      className="account-pill"
      aria-label={isAuthenticated ? t('ariaYourAccount') : t('ariaLogIn')}
    >
      <span aria-hidden>👤</span>
      <span className="account-label">{label}</span>
    </Link>
  );
}
