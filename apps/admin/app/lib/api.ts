// Typed fetch client for the Town Basket admin REST API.
//
// Reuses the storefront's conventions: a thin `apiFetch` wrapper that resolves
// URLs per execution context (server/browser), maps non-2xx to a typed
// `ApiError`, and a separate `apiMutate` for POSTing status transitions. The
// admin queue's live updates use EventSource against the public base URL.
//
// M4 Identity: admin endpoints (`/api/v1/admin/**`) are now SECURED
// (STORE_STAFF/ADMIN). Every admin call attaches `Authorization: Bearer
// <accessToken>` from the localStorage token store; on a 401 we attempt ONE
// rotating refresh (contract §2 POST /auth/refresh) and retry. If the refresh
// fails we clear the session and throw `AuthRequiredError`, which the UI treats
// as "needs login". The SSE stream can't send headers, so its token rides as a
// `?token=` query param (contract §6).

import {
  clearAuth,
  getAccessToken,
  getRefreshToken,
  updateTokens,
} from './auth';
import type {
  AuthResponse,
  Order,
  Page,
  TokenPair,
  TransitionRequest,
} from './types';

// Resolve the API base URL per execution context:
//  - Browser: the publicly reachable URL (NEXT_PUBLIC_API_BASE_URL).
//  - Server (SSR / inside Docker): an internal URL when provided
//    (INTERNAL_API_BASE_URL, e.g. http://api:8080/api/v1 under docker-compose).
const PUBLIC_API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080/api/v1';

export function getApiBaseUrl(): string {
  if (typeof window === 'undefined') {
    return process.env.INTERNAL_API_BASE_URL ?? PUBLIC_API_BASE_URL;
  }
  return PUBLIC_API_BASE_URL;
}

// Public (browser) base URL — used for EventSource stream URLs.
export const API_BASE_URL = PUBLIC_API_BASE_URL;

export class ApiError extends Error {
  readonly status: number;
  readonly url: string;
  constructor(status: number, url: string, message?: string) {
    super(message ?? `API request failed (${status}) for ${url}`);
    this.name = 'ApiError';
    this.status = status;
    this.url = url;
  }
}

/**
 * Thrown when an admin call cannot be authenticated (no token, or a 401 that
 * the rotating refresh could not recover). The session has been cleared; the
 * UI must drop to the login gate.
 */
export class AuthRequiredError extends Error {
  constructor(message = 'Session expired — please log in again.') {
    super(message);
    this.name = 'AuthRequiredError';
  }
}

function buildUrl(path: string, query?: Record<string, unknown>): string {
  const base = getApiBaseUrl().replace(/\/$/, '');
  const url = new URL(`${base}${path.startsWith('/') ? path : `/${path}`}`);
  if (query) {
    for (const [key, value] of Object.entries(query)) {
      if (value === undefined || value === null || value === '') continue;
      url.searchParams.set(key, String(value));
    }
  }
  return url.toString();
}

async function errorFromResponse(res: Response, url: string): Promise<ApiError> {
  let detail = '';
  try {
    detail = await res.text();
  } catch {
    /* ignore */
  }
  return new ApiError(res.status, url, detail || res.statusText);
}

// ---- Auth: rotating refresh ------------------------------------------------

// Coalesce concurrent refreshes (e.g. several admin calls 401 at once) into a
// single rotation so we don't burn multiple refresh tokens / race the store.
let refreshInFlight: Promise<boolean> | null = null;

/**
 * Attempt one rotating refresh using the stored refresh token. On success the
 * new token pair is persisted and `true` is returned; on failure the session
 * is cleared and `false` is returned. Returns `false` immediately when there is
 * no refresh token to present.
 */
function tryRefresh(): Promise<boolean> {
  if (refreshInFlight) return refreshInFlight;
  refreshInFlight = (async () => {
    try {
      const refreshToken = getRefreshToken();
      if (!refreshToken) return false;
      const pair = await refreshTokens(refreshToken);
      updateTokens({
        accessToken: pair.accessToken,
        refreshToken: pair.refreshToken,
      });
      return true;
    } catch {
      clearAuth();
      return false;
    } finally {
      // Allow the next 401 to start a fresh rotation.
      refreshInFlight = null;
    }
  })();
  return refreshInFlight;
}

function withAuthHeader(headers: Record<string, string>): Record<string, string> {
  const token = getAccessToken();
  if (token) headers.Authorization = `Bearer ${token}`;
  return headers;
}

// ---- Core request helpers (auth-aware) -------------------------------------

/**
 * GET an admin resource as JSON. Attaches the Bearer token; on a 401, performs
 * one rotating refresh and retries once. If refresh fails, clears the session
 * and throws `AuthRequiredError`.
 */
async function apiFetch<T>(
  path: string,
  query?: Record<string, unknown>,
): Promise<T> {
  const url = buildUrl(path, query);

  const run = async (): Promise<Response> => {
    try {
      return await fetch(url, {
        headers: withAuthHeader({ Accept: 'application/json' }),
        cache: 'no-store',
      });
    } catch (cause) {
      throw new ApiError(0, url, `Network error reaching API: ${String(cause)}`);
    }
  };

  let res = await run();
  if (res.status === 401) {
    const refreshed = await tryRefresh();
    if (!refreshed) throw new AuthRequiredError();
    res = await run();
    if (res.status === 401) {
      clearAuth();
      throw new AuthRequiredError();
    }
  }
  if (!res.ok) throw await errorFromResponse(res, url);
  const text = await res.text();
  return (text ? JSON.parse(text) : null) as T;
}

/**
 * Send a mutating admin request as JSON. Attaches the Bearer token; on a 401,
 * performs one rotating refresh and retries once (the body is re-sent). If
 * refresh fails, clears the session and throws `AuthRequiredError`.
 */
async function apiMutate<T>(
  method: 'POST' | 'PUT' | 'DELETE',
  path: string,
  body?: unknown,
): Promise<T> {
  const url = buildUrl(path);

  const run = async (): Promise<Response> => {
    const headers: Record<string, string> = withAuthHeader({
      Accept: 'application/json',
    });
    const init: RequestInit = { method, headers, cache: 'no-store' };
    if (body !== undefined) {
      headers['Content-Type'] = 'application/json';
      init.body = JSON.stringify(body);
    }
    try {
      return await fetch(url, init);
    } catch (cause) {
      throw new ApiError(0, url, `Network error reaching API: ${String(cause)}`);
    }
  };

  let res = await run();
  if (res.status === 401) {
    const refreshed = await tryRefresh();
    if (!refreshed) throw new AuthRequiredError();
    res = await run();
    if (res.status === 401) {
      clearAuth();
      throw new AuthRequiredError();
    }
  }
  if (!res.ok) throw await errorFromResponse(res, url);
  const text = await res.text();
  return (text ? JSON.parse(text) : null) as T;
}

/**
 * POST to a PUBLIC `/auth/**` endpoint (no Bearer; no refresh-retry). Used for
 * login/refresh/logout, which mint or revoke tokens themselves.
 */
async function authPost<T>(path: string, body: unknown): Promise<T> {
  const url = buildUrl(path);
  let res: Response;
  try {
    res = await fetch(url, {
      method: 'POST',
      headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
      cache: 'no-store',
    });
  } catch (cause) {
    throw new ApiError(0, url, `Network error reaching API: ${String(cause)}`);
  }
  if (!res.ok) throw await errorFromResponse(res, url);
  const text = await res.text();
  return (text ? JSON.parse(text) : null) as T;
}

// ---- Auth endpoints (contract §2, all PUBLIC) ------------------------------

/** POST /auth/staff/login — email+password staff/admin login. 401 on bad creds. */
export function staffLogin(
  email: string,
  password: string,
): Promise<AuthResponse> {
  return authPost<AuthResponse>('/auth/staff/login', { email, password });
}

/** POST /auth/refresh — rotate the refresh token for a fresh access+refresh pair. */
export function refreshTokens(refreshToken: string): Promise<TokenPair> {
  return authPost<TokenPair>('/auth/refresh', { refreshToken });
}

/** POST /auth/logout — revoke a refresh token (idempotent; always 204). */
export async function logout(refreshToken: string): Promise<void> {
  await authPost<null>('/auth/logout', { refreshToken });
}

// ---- Admin endpoint functions ----------------------------------------------

/** GET /admin/orders?status=&page=&size= — paged order queue. */
export function getAdminOrders(
  status?: string,
  page = 0,
  size = 50,
): Promise<Page<Order>> {
  return apiFetch<Page<Order>>('/admin/orders', { status, page, size });
}

/** POST /admin/orders/{id}/transitions — advance an order's status. */
export function transitionOrder(
  id: string,
  req: TransitionRequest,
): Promise<Order> {
  return apiMutate<Order>(
    'POST',
    `/admin/orders/${encodeURIComponent(id)}/transitions`,
    req,
  );
}

/**
 * URL for the admin order SSE stream (GET /admin/orders/stream). Consumed with
 * `new EventSource(...)`, so it uses the public (browser-reachable) base URL.
 *
 * EventSource cannot set an `Authorization` header, so per contract §6 the
 * access token is supplied as the `token` query param; the security layer
 * accepts it for GET requests. Returns the bare URL when no token is stored
 * (the stream will 401 — the queue surfaces that as "log in again").
 */
export function adminOrderStreamUrl(): string {
  const base = `${API_BASE_URL.replace(/\/$/, '')}/admin/orders/stream`;
  const token = getAccessToken();
  if (!token) return base;
  const url = new URL(base);
  url.searchParams.set('token', token);
  return url.toString();
}
