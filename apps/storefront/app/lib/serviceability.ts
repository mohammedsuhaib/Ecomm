// Client-side helpers for the serviceability gate: persisting the result and
// reading it back. The gate itself (geolocation prompt + manual fallback +
// API call) lives in the LocationGate client component.

import type { ServiceabilityResult } from './types';

const SERVICEABILITY_KEY = 'tb.serviceability.v1';

const BROWSE_KEY = 'tb.serviceability.browse.v1';

// A saved result is trusted as-is for this long. Past it the location is still
// remembered and shown, but re-checked quietly in the background (see
// needsRefresh) so a radius change still reaches the customer. It used to be
// the point where the location was forgotten, which put the "Where should we
// deliver?" popup back in front of a returning customer every half hour.
const FRESH_MS = 1000 * 60 * 30; // 30 min

// How long a saved location is kept at all. A different address entirely is
// the customer's to enter, via the header pill; checkout re-checks against the
// submitted coordinates regardless.
const KEEP_MS = 1000 * 60 * 60 * 24 * 90; // 90 days

// "Just browse for now" is remembered this long, so it is not asked again on
// every page load.
const BROWSE_MS = 1000 * 60 * 60 * 24 * 7; // 7 days

export interface StoredServiceability {
  result: ServiceabilityResult;
  lat: number;
  lng: number;
  checkedAt: number; // epoch ms
}

export function loadServiceability(): StoredServiceability | null {
  if (typeof window === 'undefined') return null;
  try {
    const raw = window.localStorage.getItem(SERVICEABILITY_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as StoredServiceability;
    if (
      !parsed ||
      typeof parsed.checkedAt !== 'number' ||
      !parsed.result ||
      typeof parsed.result.serviceable !== 'boolean'
    ) {
      return null;
    }
    if (Date.now() - parsed.checkedAt > KEEP_MS) return null; // long gone
    return parsed;
  } catch {
    return null;
  }
}

/** Whether a stored result is old enough to re-check in the background. */
export function needsRefresh(stored: StoredServiceability): boolean {
  return Date.now() - stored.checkedAt > FRESH_MS;
}

/** Whether the customer recently chose "Just browse for now". */
export function browseChoiceActive(): boolean {
  if (typeof window === 'undefined') return false;
  try {
    const at = Number(window.localStorage.getItem(BROWSE_KEY) ?? '0');
    return at > 0 && Date.now() - at < BROWSE_MS;
  } catch {
    return false;
  }
}

/** Remember "Just browse for now" so the popup does not return on every load. */
export function rememberBrowseChoice(): void {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.setItem(BROWSE_KEY, String(Date.now()));
  } catch {
    /* private mode: the popup simply asks again next visit */
  }
}

export function saveServiceability(
  result: ServiceabilityResult,
  lat: number,
  lng: number,
): void {
  if (typeof window === 'undefined') return;
  const payload: StoredServiceability = {
    result,
    lat,
    lng,
    checkedAt: Date.now(),
  };
  try {
    window.localStorage.setItem(SERVICEABILITY_KEY, JSON.stringify(payload));
    window.localStorage.removeItem(BROWSE_KEY); // they gave us a location after all
  } catch {
    /* storage may be unavailable (private mode); gate still works in-session */
  }
}
