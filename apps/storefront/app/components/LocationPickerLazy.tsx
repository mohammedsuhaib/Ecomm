'use client';

import dynamic from 'next/dynamic';
import { useTranslations } from 'next-intl';
import type { LocationPickerProps } from './LocationPicker';

/**
 * {@link LocationPicker}, loaded on demand.
 *
 * <p>The picker pulls in {@code @react-google-maps/api}, which is 32.7 KB
 * gzipped — the single largest thing the storefront ships. Three screens import
 * it (the location gate, checkout, and the saved-address form), and because the
 * gate lives in the root layout that chunk was in the entry graph of every
 * route, including the home page a first-time visitor waits on. Yet the map is
 * only ever rendered once someone opens a picker: a returning customer whose
 * location is already stored never sees one.
 *
 * <p>So this is the one dynamic boundary all three screens import. The chunk is
 * fetched when a picker first mounts, and the fallback below holds the map's
 * space so the surrounding form does not jump when it arrives.
 *
 * <p>{@code ssr: false} is not an optimisation here, it is required: the picker
 * must never initialise during a server render (see its own header comment).
 */
const LocationPicker = dynamic(() => import('./LocationPicker'), {
  ssr: false,
  loading: () => <LocationPickerFallback />,
});

/**
 * Deliberately the same markup the picker itself shows while the Google SDK
 * loads, so the two loading states look identical and reserve the same box —
 * the form does not shift when the chunk arrives and again when the map does.
 */
function LocationPickerFallback() {
  const t = useTranslations('location');
  return (
    <div className="location-picker">
      <div className="location-map location-map-loading" role="status" aria-live="polite">
        <span className="muted">{t('loadingMap')}</span>
      </div>
    </div>
  );
}

export default function LocationPickerLazy(props: LocationPickerProps) {
  return <LocationPicker {...props} />;
}
