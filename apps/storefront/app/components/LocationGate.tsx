'use client';

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react';
import { useTranslations } from 'next-intl';
import { checkServiceability } from '@/app/lib/api';
import { formatDistance } from '@/app/lib/format';
import {
  loadServiceability,
  saveServiceability,
  type StoredServiceability,
} from '@/app/lib/serviceability';
import type { ServiceabilityResult } from '@/app/lib/types';
import LocationPicker from './LocationPicker';

// ---- Context so any component (e.g. the header pill) can re-open the gate ----

interface LocationGateContextValue {
  reopen: () => void;
}
const LocationGateContext = createContext<LocationGateContextValue>({
  reopen: () => {},
});

export function useLocationGate(): LocationGateContextValue {
  return useContext(LocationGateContext);
}

type Phase =
  | 'idle' // not yet decided whether to show the gate
  | 'prompt' // asking for location (geolocation + manual fallback)
  | 'checking' // calling the API
  | 'serviceable' // gate dismissed, catalogue visible
  | 'not-serviceable'; // blocking "we don't deliver here" screen

/**
 * The serviceability gate. On first visit it prompts for the customer's
 * location (browser Geolocation, with a manual lat/lng fallback and a
 * "browse anyway" escape hatch), calls /serviceability/check, and persists
 * the result. If not serviceable it shows a clear blocking screen; otherwise
 * it renders the catalogue (children).
 *
 * Location is set with the shared {@link LocationPicker} — a Google-Maps pin-drop
 * when NEXT_PUBLIC_GOOGLE_MAPS_API_KEY is configured, otherwise a "use my current
 * location" geolocation fallback (ARCHITECTURE.md §3.7, §4.1).
 */
export default function LocationGate({
  children,
}: {
  children: React.ReactNode;
}) {
  const t = useTranslations('location');
  const [phase, setPhase] = useState<Phase>('idle');
  const [result, setResult] = useState<ServiceabilityResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  // The point chosen via the map pin-drop (LocationPicker), as strings.
  const [pickLat, setPickLat] = useState('');
  const [pickLng, setPickLng] = useState('');

  // Decide initial phase from persisted result (runs client-side only).
  useEffect(() => {
    const stored: StoredServiceability | null = loadServiceability();
    if (stored) {
      // Prefill the picker with the last-used point so re-opening shows it.
      setPickLat(String(stored.lat));
      setPickLng(String(stored.lng));
    }
    if (stored?.result.serviceable) {
      setResult(stored.result);
      setPhase('serviceable');
    } else if (stored && !stored.result.serviceable) {
      setResult(stored.result);
      setPhase('not-serviceable');
    } else {
      setPhase('prompt');
    }
  }, []);

  const runCheck = useCallback(async (lat: number, lng: number) => {
    setPhase('checking');
    setError(null);
    try {
      const res = await checkServiceability(lat, lng);
      saveServiceability(res, lat, lng);
      setResult(res);
      setPhase(res.serviceable ? 'serviceable' : 'not-serviceable');
      // Let the header indicator refresh.
      window.dispatchEvent(new Event('tb:serviceability-changed'));
    } catch {
      setError(t('errorCheck'));
      setPhase('prompt');
    }
  }, []);

  // Check the point chosen on the map / via "use my current location".
  const checkPicked = useCallback(() => {
    const lat = Number.parseFloat(pickLat);
    const lng = Number.parseFloat(pickLng);
    if (
      Number.isNaN(lat) ||
      Number.isNaN(lng) ||
      lat < -90 ||
      lat > 90 ||
      lng < -180 ||
      lng > 180
    ) {
      setError(t('errorSetFirst'));
      return;
    }
    runCheck(lat, lng);
  }, [pickLat, pickLng, runCheck]);

  // "Browse anyway" — let them into the catalogue without a serviceable
  // result. Checkout (M3) will re-verify, per the architecture.
  const browseAnyway = useCallback(() => {
    setPhase('serviceable');
  }, []);

  const reopen = useCallback(() => {
    setError(null);
    setPhase('prompt');
  }, []);

  const ctx = useMemo<LocationGateContextValue>(() => ({ reopen }), [reopen]);

  return (
    <LocationGateContext.Provider value={ctx}>
      {/* The shell (header/catalogue/footer) always renders; the gate and
          not-serviceable screens are fixed overlays on top of it. */}
      {children}

      {phase === 'not-serviceable' && (
        <NotServiceableScreen result={result} onRetry={reopen} />
      )}

      {(phase === 'prompt' || phase === 'checking') && (
        <div className="gate-overlay" role="dialog" aria-modal="true">
          <div className="gate-card">
            <h2>{t('gateTitle')}</h2>
            <p>{t('gateBody')}</p>

            {error && <p className="gate-error">{error}</p>}

            <LocationPicker
              lat={pickLat}
              lng={pickLng}
              onChange={(la, ln) => {
                setPickLat(String(la));
                setPickLng(String(ln));
                setError(null);
              }}
            />

            <div className="gate-actions">
              <button
                type="button"
                className="btn btn-block"
                onClick={checkPicked}
                disabled={phase === 'checking'}
              >
                {phase === 'checking' ? t('checking') : t('check')}
              </button>
              <button
                type="button"
                className="btn btn-outline btn-block"
                onClick={browseAnyway}
              >
                {t('browse')}
              </button>
            </div>
          </div>
        </div>
      )}
    </LocationGateContext.Provider>
  );
}

function NotServiceableScreen({
  result,
  onRetry,
}: {
  result: ServiceabilityResult | null;
  onRetry: () => void;
}) {
  const t = useTranslations('location');
  return (
    <div className="gate-overlay" role="dialog" aria-modal="true">
      <div className="gate-card not-serviceable">
        <div className="big-emoji" aria-hidden>
          🛵💨
        </div>
        <h2>{t('nsTitle')}</h2>
        <p>
          {result
            ? t('nsRangeWithStore', { store: result.storeName })
            : t('nsRangeNoStore')}
          {result &&
            t('nsDistance', {
              distance: formatDistance(result.distanceMeters),
              radius: formatDistance(result.radiusMeters),
            })}
        </p>
        <div className="gate-actions">
          <button type="button" className="btn btn-block" onClick={onRetry}>
            {t('nsRetry')}
          </button>
        </div>
      </div>
    </div>
  );
}
