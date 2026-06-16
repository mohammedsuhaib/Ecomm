'use client';

import { useEffect, useState } from 'react';
import { loadServiceability } from '@/app/lib/serviceability';
import { useLocationGate } from './LocationGate';

/**
 * Header pill showing the customer's delivery location status. Clicking it
 * re-opens the location gate so they can change/re-check their location.
 */
export default function LocationIndicator() {
  const { reopen } = useLocationGate();
  const [label, setLabel] = useState('Set location');

  useEffect(() => {
    const stored = loadServiceability();
    if (stored?.result.serviceable) {
      setLabel(`Delivering near you`);
    } else if (stored && !stored.result.serviceable) {
      setLabel('Out of range');
    }
    // Re-sync when the gate updates storage.
    const onStorage = () => {
      const s = loadServiceability();
      setLabel(
        s?.result.serviceable
          ? 'Delivering near you'
          : s
            ? 'Out of range'
            : 'Set location',
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
      title="Change delivery location"
    >
      <span aria-hidden>📍</span>
      <span>{label}</span>
    </button>
  );
}
