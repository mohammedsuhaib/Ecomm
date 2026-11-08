// Client-side helpers for the serviceability gate: persisting the result and
// reading it back. The gate itself (geolocation prompt + manual fallback +
// API call) lives in the LocationGate client component.

import type { ServiceabilityResult } from './types';

const SERVICEABILITY_KEY = 'tb.serviceability.v1';

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

/**
 * Where the device is right now, or null when we cannot tell (no geolocation
 * API, permission blocked or refused, timeout, no fix).
 *
 * <p>A blocked permission is detected up front where the Permissions API exists,
 * so a customer who said no is not shown the browser's prompt again on every
 * page load. When permission has never been asked, this is what triggers the
 * browser's own prompt.
 */
export async function currentDevicePosition(): Promise<{ lat: number; lng: number } | null> {
  if (typeof navigator === 'undefined' || !navigator.geolocation) return null;
  try {
    const status = await navigator.permissions?.query({ name: 'geolocation' });
    if (status?.state === 'denied') return null;
  } catch {
    /* Permissions API missing or refuses 'geolocation': just ask */
  }
  return new Promise((resolve) => {
    navigator.geolocation.getCurrentPosition(
      (pos) => resolve({ lat: pos.coords.latitude, lng: pos.coords.longitude }),
      () => resolve(null),
      { enableHighAccuracy: false, timeout: 8000, maximumAge: 5 * 60 * 1000 },
    );
  });
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
  } catch {
    /* storage may be unavailable (private mode); gate still works in-session */
  }
}
