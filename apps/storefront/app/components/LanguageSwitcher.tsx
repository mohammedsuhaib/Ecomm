'use client';

import { useLocale, useTranslations } from 'next-intl';
import { useTransition } from 'react';
import { useRouter } from 'next/navigation';
import { setLocaleCookie } from '@/i18n/actions';
import { locales, localeLabels } from '@/i18n/config';

/**
 * Header language toggle (EN / ಕನ್ನಡ). Persists the choice in a cookie via a
 * server action, then refreshes so server components re-render in the chosen
 * language. The initial language is auto-detected from the device on first
 * visit (i18n/request.ts); this lets the customer override it.
 */
export default function LanguageSwitcher() {
  const active = useLocale();
  const t = useTranslations('common');
  const router = useRouter();
  const [pending, startTransition] = useTransition();

  function change(next: string) {
    if (next === active || pending) return;
    startTransition(async () => {
      await setLocaleCookie(next);
      router.refresh();
    });
  }

  return (
    <div className="lang-switch" role="group" aria-label={t('language')}>
      {locales.map((l) => (
        <button
          key={l}
          type="button"
          className={`lang-option${l === active ? ' active' : ''}`}
          aria-pressed={l === active}
          disabled={pending}
          onClick={() => change(l)}
          lang={l}
        >
          {localeLabels[l]}
        </button>
      ))}
    </div>
  );
}
