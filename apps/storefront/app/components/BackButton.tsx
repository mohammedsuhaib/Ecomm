'use client';

import { usePathname, useRouter } from 'next/navigation';
import { useTranslations } from 'next-intl';

/**
 * Mobile-only back control (F4). Shown on phone widths on every route except
 * home; hidden on desktop via CSS (.mobile-back is display:none ≥640px). Uses
 * router.back() when there is history to pop, otherwise falls back to home so
 * a deep-linked first visit still has somewhere to go.
 */
export default function BackButton() {
  const router = useRouter();
  const pathname = usePathname();
  const t = useTranslations('common');

  // No back control on the home page itself.
  if (pathname === '/') return null;

  function onClick() {
    if (typeof window !== 'undefined' && window.history.length > 1) {
      router.back();
    } else {
      router.push('/');
    }
  }

  return (
    <button
      type="button"
      className="mobile-back"
      onClick={onClick}
      aria-label={t('goBack')}
      title={t('goBack')}
    >
      <span aria-hidden>←</span>
    </button>
  );
}
