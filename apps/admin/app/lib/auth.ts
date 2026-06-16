// SSR-safe auth token store for the admin app (M4 Identity).
//
// Staff/admin log in with email+password (contract §2 POST /auth/staff/login)
// and we hold the resulting access token, rotating refresh token, and cached
// UserDto in localStorage so a reload keeps the session.
//
// SECURITY TRADE-OFF: tokens live in localStorage, which is readable by any
// JavaScript on the origin — so a successful XSS could exfiltrate them. We
// accept this for an internal staff tool (no httpOnly-cookie/BFF layer here);
// the access token is short-lived (15 min, contract §0) and refresh tokens
// rotate on every use, limiting the blast radius. Never log token values.

import type { UserDto } from './types';

const STORAGE_KEY = 'tb.admin.auth.v1';

export interface StoredAuth {
  accessToken: string;
  refreshToken: string;
  user: UserDto;
}

/** Guard against SSR / non-browser execution where `localStorage` is absent. */
function hasStorage(): boolean {
  return typeof window !== 'undefined' && typeof window.localStorage !== 'undefined';
}

/** Read the full stored auth bundle, or null if absent / unparseable. */
export function getStoredAuth(): StoredAuth | null {
  if (!hasStorage()) return null;
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Partial<StoredAuth>;
    if (
      typeof parsed?.accessToken === 'string' &&
      typeof parsed?.refreshToken === 'string' &&
      parsed?.user
    ) {
      return parsed as StoredAuth;
    }
    return null;
  } catch {
    return null;
  }
}

/** The current access token for the `Authorization: Bearer` header, or null. */
export function getAccessToken(): string | null {
  return getStoredAuth()?.accessToken ?? null;
}

/** The current rotating refresh token, or null. */
export function getRefreshToken(): string | null {
  return getStoredAuth()?.refreshToken ?? null;
}

/** The cached signed-in user, or null. */
export function getStoredUser(): UserDto | null {
  return getStoredAuth()?.user ?? null;
}

/** Persist a freshly issued auth bundle (after login). */
export function saveAuth(auth: StoredAuth): void {
  if (!hasStorage()) return;
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(auth));
  } catch {
    /* storage full / disabled — session simply won't persist */
  }
}

/**
 * Update just the token pair after a rotating refresh, keeping the cached user.
 * No-op if there is no stored session to update.
 */
export function updateTokens(tokens: {
  accessToken: string;
  refreshToken: string;
}): void {
  const current = getStoredAuth();
  if (!current) return;
  saveAuth({ ...current, ...tokens });
}

/** Wipe the stored session (logout, or after an unrecoverable 401). */
export function clearAuth(): void {
  if (!hasStorage()) return;
  try {
    window.localStorage.removeItem(STORAGE_KEY);
  } catch {
    /* ignore */
  }
}
