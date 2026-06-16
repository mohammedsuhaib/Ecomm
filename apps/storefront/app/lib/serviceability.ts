// Client-side helpers for the serviceability gate: persisting the result and
// reading it back. The gate itself (geolocation prompt + manual fallback +
// API call) lives in the LocationGate client component.

import type { ServiceabilityResult } from './types';

export const SERVICEABILITY_KEY = 'tb.serviceability.v1';

// Cached serviceable result is trusted for this long before we re-check.
// Keeps the gate lightweight while still re-validating periodically.
const TTL_MS = 1000 * 60 * 60 * 24; // 24h

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
    if (Date.now() - parsed.checkedAt > TTL_MS) return null; // stale
    return parsed;
  } catch {
    return null;
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
  } catch {
    /* storage may be unavailable (private mode); gate still works in-session */
  }
}

export function clearServiceability(): void {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.removeItem(SERVICEABILITY_KEY);
  } catch {
    /* ignore */
  }
}
