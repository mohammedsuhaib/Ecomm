'use client';

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react';
import { checkServiceability } from '@/app/lib/api';
import { formatDistance } from '@/app/lib/format';
import {
  loadServiceability,
  saveServiceability,
  type StoredServiceability,
} from '@/app/lib/serviceability';
import type { ServiceabilityResult } from '@/app/lib/types';

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
 * Lightweight by design — no paid maps SDK, just the Geolocation API +
 * manual coordinate entry (ARCHITECTURE.md §3.7, §4.1).
 */
export default function LocationGate({
  children,
}: {
  children: React.ReactNode;
}) {
  const [phase, setPhase] = useState<Phase>('idle');
  const [result, setResult] = useState<ServiceabilityResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [manualLat, setManualLat] = useState('');
  const [manualLng, setManualLng] = useState('');

  // Decide initial phase from persisted result (runs client-side only).
  useEffect(() => {
    const stored: StoredServiceability | null = loadServiceability();
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
      setError(
        'We couldn’t check your location right now. Please try again, or enter coordinates manually.',
      );
      setPhase('prompt');
    }
  }, []);

  const useBrowserLocation = useCallback(() => {
    setError(null);
    if (typeof navigator === 'undefined' || !navigator.geolocation) {
      setError(
        'Your browser doesn’t support location. Please enter coordinates manually.',
      );
      return;
    }
    setPhase('checking');
    navigator.geolocation.getCurrentPosition(
      (pos) => runCheck(pos.coords.latitude, pos.coords.longitude),
      () => {
        setError(
          'Location permission was denied. Enter coordinates manually or browse anyway.',
        );
        setPhase('prompt');
      },
      { enableHighAccuracy: true, timeout: 10000, maximumAge: 60000 },
    );
  }, [runCheck]);

  const submitManual = useCallback(
    (e: React.FormEvent) => {
      e.preventDefault();
      const lat = Number.parseFloat(manualLat);
      const lng = Number.parseFloat(manualLng);
      if (
        Number.isNaN(lat) ||
        Number.isNaN(lng) ||
        lat < -90 ||
        lat > 90 ||
        lng < -180 ||
        lng > 180
      ) {
        setError('Please enter a valid latitude and longitude.');
        return;
      }
      runCheck(lat, lng);
    },
    [manualLat, manualLng, runCheck],
  );

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
            <h2>🧺 Where should we deliver?</h2>
            <p>
              We deliver groceries within 5&nbsp;km of our store. Share your
              location so we can check if you’re in range.
            </p>

            {error && <p className="gate-error">{error}</p>}

            <div className="gate-actions">
              <button
                type="button"
                className="btn btn-block"
                onClick={useBrowserLocation}
                disabled={phase === 'checking'}
              >
                {phase === 'checking'
                  ? 'Checking…'
                  : '📍 Use my current location'}
              </button>
            </div>

            <div className="gate-divider">— or enter coordinates —</div>

            <form onSubmit={submitManual}>
              <div className="field">
                <label htmlFor="lat">Latitude</label>
                <input
                  id="lat"
                  inputMode="decimal"
                  placeholder="e.g. 12.9716"
                  value={manualLat}
                  onChange={(e) => setManualLat(e.target.value)}
                />
              </div>
              <div className="field">
                <label htmlFor="lng">Longitude</label>
                <input
                  id="lng"
                  inputMode="decimal"
                  placeholder="e.g. 77.5946"
                  value={manualLng}
                  onChange={(e) => setManualLng(e.target.value)}
                />
              </div>
              <div className="gate-actions">
                <button
                  type="submit"
                  className="btn btn-outline btn-block"
                  disabled={phase === 'checking'}
                >
                  Check this location
                </button>
                <button
                  type="button"
                  className="btn btn-outline btn-block"
                  onClick={browseAnyway}
                >
                  Just browse for now
                </button>
              </div>
            </form>
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
  return (
    <div className="gate-overlay" role="dialog" aria-modal="true">
      <div className="gate-card not-serviceable">
        <div className="big-emoji" aria-hidden>
          🛵💨
        </div>
        <h2>Sorry, we don’t deliver to your location yet</h2>
        <p>
          We currently deliver within 5&nbsp;km of our store
          {result ? ` (${result.storeName})` : ''}.
          {result &&
            ` You’re about ${formatDistance(result.distanceMeters)} away, just outside our ${formatDistance(
              result.radiusMeters,
            )} delivery range.`}
        </p>
        <div className="gate-actions">
          <button type="button" className="btn btn-block" onClick={onRetry}>
            Try a different location
          </button>
        </div>
      </div>
    </div>
  );
}
