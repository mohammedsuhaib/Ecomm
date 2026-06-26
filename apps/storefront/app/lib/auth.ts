// Client-side auth persistence for customers (M4 Identity).
//
// We store the access + refresh tokens and a cached UserDto in localStorage so
// the session survives reloads and navigation. The server is the source of
// truth; the cached user is only used for instant UI (header, prefill) and is
// refreshed via GET /me.
//
// SECURITY TRADE-OFF: tokens in localStorage are readable by any JS running on
// the page, so a successful XSS would leak them. This is an accepted MVP
// trade-off (no httpOnly-cookie infra yet). Mitigations in place: access tokens
// are short-lived (~15 min) and refresh tokens rotate on every use (a stolen
// refresh token is invalidated the next time the legit client refreshes). The
// Bearer header is only ever attached to our own API base URL, and tokens are
// never logged.

import type { TokenPair, UserDto } from './types';

export const AUTH_KEY = 'tb.auth.v1';

export interface StoredAuth {
  accessToken: string;
  refreshToken: string;
  user: UserDto;
}

export function loadAuth(): StoredAuth | null {
  if (typeof window === 'undefined') return null;
  try {
    const raw = window.localStorage.getItem(AUTH_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as StoredAuth;
    if (
      !parsed ||
      typeof parsed.accessToken !== 'string' ||
      typeof parsed.refreshToken !== 'string' ||
      !parsed.user ||
      typeof parsed.user.id !== 'number'
    ) {
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}

export function saveAuth(auth: StoredAuth): void {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.setItem(AUTH_KEY, JSON.stringify(auth));
    // Let same-tab listeners (e.g. the header) react without a reload.
    window.dispatchEvent(new Event('tb:auth-changed'));
  } catch {
    /* storage may be unavailable (private mode); session still works in-tab */
  }
}

export function clearAuth(): void {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.removeItem(AUTH_KEY);
    window.dispatchEvent(new Event('tb:auth-changed'));
  } catch {
    /* ignore */
  }
}

// --- Granular accessors used by the authenticated fetch helper -----------

export function loadAccessToken(): string | null {
  return loadAuth()?.accessToken ?? null;
}

export function loadRefreshToken(): string | null {
  return loadAuth()?.refreshToken ?? null;
}

export function loadUser(): UserDto | null {
  return loadAuth()?.user ?? null;
}

/** Persist a freshly rotated token pair, keeping the cached user. */
export function saveTokens(tokens: TokenPair): void {
  const existing = loadAuth();
  if (!existing) return; // no session to update
  saveAuth({
    accessToken: tokens.accessToken,
    refreshToken: tokens.refreshToken,
    user: existing.user,
  });
}

/** Update just the cached user (after GET /me), keeping current tokens. */
export function saveUser(user: UserDto): void {
  const existing = loadAuth();
  if (!existing) return;
  saveAuth({ ...existing, user });
}
