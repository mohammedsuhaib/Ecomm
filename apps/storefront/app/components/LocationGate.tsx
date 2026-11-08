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
import { checkServiceability, getStore, listAddresses } from '@/app/lib/api';
import { loadAuth } from '@/app/lib/auth';
import { formatDistance } from '@/app/lib/format';
import { distanceMeters } from '@/app/lib/geo';
import {
  currentDevicePosition,
  loadServiceability,
  needsRefresh,
  saveServiceability,
  type StoredServiceability,
} from '@/app/lib/serviceability';
import type { SavedAddress, ServiceabilityResult, Store } from '@/app/lib/types';
import LocationPicker from './LocationPickerLazy';

// ---- Context so any component (e.g. the header pill) can re-open the gate ----

interface LocationGateContextValue {
  reopen: () => void;
  /**
   * True while a gate overlay is covering the shell.
   *
   * <p>The overlay is only 55% opaque and traps no focus, so anything the shell
   * renders underneath stays visible through it and reachable by keyboard.
   * That is fine for the catalogue, which is the backdrop by design, but not
   * for something that asks the customer a second question — see InstallPrompt,
   * which stands down while this is true.
   */
  blocking: boolean;
}
const LocationGateContext = createContext<LocationGateContextValue>({
  reopen: () => {},
  blocking: false,
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
  // The signed-in customer's saved addresses, offered on the screens below so
  // someone away from home can still deliver to one of them.
  const [saved, setSaved] = useState<SavedAddress[]>([]);
  // Where the store is, so each saved address can show how far it is and whether
  // we deliver there. Null until fetched (or if that fails): the rows then simply
  // show no distance and none are greyed out.
  const [store, setStore] = useState<Store | null>(null);
  // A problem with the "use my current location" row on the out-of-range sheet.
  const [sheetError, setSheetError] = useState<string | null>(null);

  // Decide whether to show the popup, every time the app opens: look at where
  // the device is. In range means no popup; out of range, or no way to tell,
  // means the popup.
  //
  // A location the customer pinned by hand earlier (and that is in range) still
  // counts, so someone ordering to an address other than where they stand is not
  // nagged, and so a phone with location off or blocked keeps working after one
  // successful pin. It is also what is shown straight away, so a returning
  // customer never waits on the GPS fix to see the catalogue.
  useEffect(() => {
    let cancelled = false;
    const stored: StoredServiceability | null = loadServiceability();
    if (stored) {
      // Prefill the picker with the last-used point so re-opening shows it.
      setPickLat(String(stored.lat));
      setPickLng(String(stored.lng));
    }
    const storedOk = stored?.result.serviceable === true;
    if (storedOk) {
      setResult(stored.result);
      setPhase('serviceable');
    }

    const settle = (res: ServiceabilityResult) => {
      setResult(res);
      setPhase(res.serviceable ? 'serviceable' : 'not-serviceable');
      window.dispatchEvent(new Event('tb:serviceability-changed'));
    };

    // Away from home with nothing pinned on this phone: a signed-in customer's
    // default saved address is where they want the order to go, so use it
    // rather than asking again. True when it was in range and has been adopted.
    const adoptSavedAddress = async (): Promise<boolean> => {
      if (!loadAuth()) return false;
      try {
        const list = await listAddresses();
        const home = list.find((a) => a.isDefault) ?? list[0];
        if (!home || cancelled) return false;
        const res = await checkServiceability(home.lat, home.lng);
        if (cancelled || !res.serviceable) return false;
        setPickLat(String(home.lat));
        setPickLng(String(home.lng));
        saveServiceability(res, home.lat, home.lng);
        settle(res);
        return true;
      } catch {
        return false;
      }
    };

    (async () => {
      const here = await currentDevicePosition();
      if (cancelled) return;
      if (here) {
        try {
          const res = await checkServiceability(here.lat, here.lng);
          if (cancelled) return;
          if (res.serviceable) {
            // Remember it unless a hand-pinned address is already on file.
            if (!storedOk) saveServiceability(res, here.lat, here.lng);
            settle(storedOk ? stored.result : res);
            return;
          }
          // Out of range here; a pinned in-range address still wins, and so
          // does a saved one.
          if (storedOk) return;
          if (await adoptSavedAddress()) return;
          if (cancelled) return;
          saveServiceability(res, here.lat, here.lng);
          settle(res);
          return;
        } catch {
          /* fall through to the saved location */
        }
      }
      // No usable fix. Keep what we have, re-checking a stale one quietly.
      if (storedOk) {
        if (needsRefresh(stored)) {
          checkServiceability(stored.lat, stored.lng)
            .then((res) => {
              if (cancelled) return;
              saveServiceability(res, stored.lat, stored.lng);
              settle(res);
            })
            .catch(() => {});
        }
        return;
      }
      if (await adoptSavedAddress()) return;
      if (cancelled) return;
      if (stored) {
        setResult(stored.result);
        setPhase('not-serviceable');
      } else {
        setPhase('prompt');
      }
    })();

    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if ((phase !== 'prompt' && phase !== 'not-serviceable') || !loadAuth()) return;
    let cancelled = false;
    Promise.all([listAddresses(), getStore().catch(() => null)])
      .then(([list, shop]) => {
        if (cancelled) return;
        setSaved(list);
        setStore(shop);
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [phase]);

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
  }, [t]);

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
  }, [pickLat, pickLng, runCheck, t]);

  // "Browse anyway" — let them into the catalogue without a serviceable
  // result. Checkout (M3) will re-verify, per the architecture.
  const browseAnyway = useCallback(() => {
    setPhase('serviceable');
  }, []);

  // The "Use my current location" row on the out-of-range sheet: look again,
  // for someone who has just come home.
  const useCurrentLocation = useCallback(async () => {
    setSheetError(null);
    const here = await currentDevicePosition();
    if (!here) {
      setSheetError(t('denied'));
      return;
    }
    setPickLat(String(here.lat));
    setPickLng(String(here.lng));
    runCheck(here.lat, here.lng);
  }, [runCheck, t]);

  const reopen = useCallback(() => {
    setError(null);
    setPhase('prompt');
  }, []);

  // 'idle' counts as blocking: it is the pre-decision phase, while the device
  // position is being read, and it resolves in the mount effect above to
  // 'prompt' when there is nothing to go on. Treating it as clear would flash
  // anything underneath onto the screen just before the overlay covers it.
  const blocking = phase !== 'serviceable';

  const ctx = useMemo<LocationGateContextValue>(
    () => ({ reopen, blocking }),
    [reopen, blocking],
  );

  return (
    <LocationGateContext.Provider value={ctx}>
      {/* The shell (header/catalogue/footer) always renders; the gate and
          not-serviceable screens are fixed overlays on top of it. */}
      {children}

      {phase === 'not-serviceable' && (
        <NotServiceableScreen
          result={result}
          onAddNew={reopen}
          onUseCurrent={useCurrentLocation}
          error={sheetError}
          saved={saved}
          store={store}
          selected={{ lat: Number.parseFloat(pickLat), lng: Number.parseFloat(pickLng) }}
          onPickSaved={(a) => {
            setPickLat(String(a.lat));
            setPickLng(String(a.lng));
            runCheck(a.lat, a.lng);
          }}
        />
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

            <SavedAddresses
              heading={t('savedTitle')}
              addresses={saved}
              store={store}
              selected={{ lat: Number.parseFloat(pickLat), lng: Number.parseFloat(pickLng) }}
              onPick={(a) => {
                setPickLat(String(a.lat));
                setPickLng(String(a.lng));
                runCheck(a.lat, a.lng);
              }}
              disabled={phase === 'checking'}
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

function sameCoord(a: number, b: number): boolean {
  return Number.isFinite(a) && Number.isFinite(b) && Math.abs(a - b) < 1e-6;
}

/**
 * The customer's saved addresses, one row each: its name, how far it is from
 * the store, a "Selected" tag on the one in use, and the address itself. An
 * address outside the delivery range is tagged and cannot be chosen, since
 * picking it would only bring this screen straight back. Renders nothing for a
 * guest or a customer with none saved.
 */
function SavedAddresses({
  heading,
  addresses,
  store,
  selected,
  onPick,
  disabled,
}: {
  heading: string;
  addresses: SavedAddress[];
  store: Store | null;
  selected: { lat: number; lng: number };
  onPick: (a: SavedAddress) => void;
  disabled?: boolean;
}) {
  const t = useTranslations('location');
  if (addresses.length === 0) return null;
  return (
    <div className="gate-saved">
      <h3 className="gate-saved-title">{heading}</h3>
      <ul>
        {addresses.map((a) => {
          const meters = store ? distanceMeters(store, a) : null;
          const out = store != null && meters != null && meters > store.deliveryRadiusMeters;
          const isSelected = sameCoord(a.lat, selected.lat) && sameCoord(a.lng, selected.lng);
          return (
            <li key={a.id}>
              <button
                type="button"
                className={`gate-saved-btn${out ? ' is-out' : ''}`}
                disabled={disabled || out}
                onClick={() => onPick(a)}
              >
                <span className="gate-saved-head">
                  <strong>{a.label || t('addressFallback')}</strong>
                  {meters != null && (
                    <span className="gate-saved-dist">
                      {t('fromStore', { distance: formatDistance(meters) })}
                    </span>
                  )}
                  {isSelected && !out && <span className="gate-tag ok">{t('selected')}</span>}
                  {out && <span className="gate-tag out">{t('outOfRangeTag')}</span>}
                </span>
                <span className="gate-saved-line">{a.line}</span>
              </button>
            </li>
          );
        })}
      </ul>
    </div>
  );
}

/**
 * Shown when the chosen location is outside the delivery range: why, then a
 * "Select location" list in the usual shape: look again from here, add a new
 * address, or pick a saved one.
 */
function NotServiceableScreen({
  result,
  onAddNew,
  onUseCurrent,
  error,
  saved,
  store,
  selected,
  onPickSaved,
}: {
  result: ServiceabilityResult | null;
  onAddNew: () => void;
  onUseCurrent: () => void;
  error: string | null;
  saved: SavedAddress[];
  store: Store | null;
  selected: { lat: number; lng: number };
  onPickSaved: (a: SavedAddress) => void;
}) {
  const t = useTranslations('location');
  return (
    <div className="gate-overlay" role="dialog" aria-modal="true">
      <div className="gate-card not-serviceable">
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

        <h3 className="gate-section-title">{t('selectTitle')}</h3>
        {error && <p className="gate-error">{error}</p>}
        <div className="gate-rows">
          <button type="button" className="gate-row-btn" onClick={onUseCurrent}>
            <span className="gate-row-icon" aria-hidden>◎</span>
            {t('useCurrentPlain')}
          </button>
          <button type="button" className="gate-row-btn" onClick={onAddNew}>
            <span className="gate-row-icon" aria-hidden>+</span>
            {t('addNew')}
            <span className="gate-row-chevron" aria-hidden>›</span>
          </button>
        </div>

        <SavedAddresses
          heading={t('savedHeading')}
          addresses={saved}
          store={store}
          selected={selected}
          onPick={onPickSaved}
        />
      </div>
    </div>
  );
}
