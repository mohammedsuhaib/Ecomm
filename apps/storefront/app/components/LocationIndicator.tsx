'use client';

import { useEffect, useState } from 'react';
import { useTranslations } from 'next-intl';
import { loadServiceability } from '@/app/lib/serviceability';
import { useLocationGate } from './LocationGate';

/**
 * Header pill showing the customer's delivery location status. Clicking it
 * re-opens the location gate so they can change/re-check their location.
 */
export default function LocationIndicator() {
  const { reopen } = useLocationGate();
  const t = useTranslations('location');
  const [label, setLabel] = useState(t('setLocation'));

  useEffect(() => {
    const stored = loadServiceability();
    if (stored?.result.serviceable) {
      setLabel(t('delivering'));
    } else if (stored && !stored.result.serviceable) {
      setLabel(t('outOfRange'));
    }
    // Re-sync when the gate updates storage.
    const onStorage = () => {
      const s = loadServiceability();
      setLabel(
        s?.result.serviceable
          ? t('delivering')
          : s
            ? t('outOfRange')
            : t('setLocation'),
      );
    };
    window.addEventListener('tb:serviceability-changed', onStorage);
    window.addEventListener('storage', onStorage);
    return () => {
      window.removeEventListener('tb:serviceability-changed', onStorage);
      window.removeEventListener('storage', onStorage);
    };
  }, []);

  return (
    <button
      type="button"
      className="location-pill"
      onClick={reopen}
      title={t('changeTitle')}
    >
      <span aria-hidden>📍</span>
      <span>{label}</span>
    </button>
  );
}
