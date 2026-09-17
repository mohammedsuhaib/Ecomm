'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { useEffect, useState } from 'react';
import { useAuth } from '@/app/components/AuthProvider';
import AddressManager from './AddressManager';
import NotificationSettings from './NotificationSettings';
import OrderHistory from './OrderHistory';
import ProfileEditor from './ProfileEditor';

export default function AccountPage() {
  const router = useRouter();
  const t = useTranslations('account');
  const common = useTranslations('common');
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
    // Skeletons, not a bare text line: reserve the profile + orders space so
    // the page doesn't jump when the data lands.
    return (
      <div aria-busy="true" aria-label={t('loadingAccount')}>
        <div className="skeleton-row" />
        <div className="skeleton-row" />
        <div className="skeleton-row" />
      </div>
    );
  }

  return (
    <>
      <nav className="breadcrumb">
        <Link href="/">{common('home')}</Link> / <span>{common('account')}</span>
      </nav>

      <section className="account-section">
        <div className="account-section-head">
          <h1 className="section-title" style={{ margin: 0 }}>
            {t('yourAccount')}
          </h1>
          <button
            type="button"
            className="btn btn-outline"
            onClick={onLogout}
            disabled={loggingOut}
          >
            {loggingOut ? t('loggingOut') : t('logOut')}
          </button>
        </div>
        <ProfileEditor user={user} />
      </section>

      {/* The one notification control a customer can always reach — the order
          page's only appears while an order is in flight, so there was nowhere
          to switch this on from a device that had none. It sits directly under
          the profile because it is an account setting: below the order history
          and the address book it was the longest scroll on the page, which is
          no place for the switch someone came here to find. */}
      <NotificationSettings />

      <section className="account-section">
        <h2 className="section-title">{t('recentOrders')}</h2>
        <OrderHistory />
      </section>

      <AddressManager />
    </>
  );
}
