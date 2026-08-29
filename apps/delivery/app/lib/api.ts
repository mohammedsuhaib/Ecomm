import { clearAuth, getAccessToken, getRefreshToken, updateTokens } from './auth';
import type { AuthResponse, Order, Page, TokenPair } from './types';

const PUBLIC_BASE =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080/api/v1';

function getApiBase(): string {
  if (typeof window === 'undefined') {
    return process.env.INTERNAL_API_BASE_URL ?? PUBLIC_BASE;
  }
  return PUBLIC_BASE;
}

export class ApiError extends Error {
  readonly status: number;
  constructor(status: number, message?: string) {
    super(message ?? `API error ${status}`);
    this.name = 'ApiError';
    this.status = status;
  }
}

export class AuthRequiredError extends Error {
  constructor() {
    super('Session expired — please log in again.');
    this.name = 'AuthRequiredError';
  }
}

function url(path: string, query?: Record<string, unknown>): string {
  const base = getApiBase().replace(/\/$/, '');
  const u = new URL(`${base}${path.startsWith('/') ? path : `/${path}`}`);
  if (query) {
    for (const [k, v] of Object.entries(query)) {
      if (v == null || v === '') continue;
      u.searchParams.set(k, String(v));
    }
  }
  return u.toString();
}

async function toError(res: Response, u: string): Promise<ApiError> {
  let msg = '';
  try { msg = await res.text(); } catch { /* ignore */ }
  return new ApiError(res.status, msg || res.statusText || u);
}

// ---- rotating refresh (coalesced) ------------------------------------------

let refreshFlight: Promise<boolean> | null = null;

function tryRefresh(): Promise<boolean> {
  if (refreshFlight) return refreshFlight;
  refreshFlight = (async () => {
    try {
      const rt = getRefreshToken();
      if (!rt) return false;
      const pair = await rotateToken(rt);
      updateTokens(pair);
      return true;
    } catch {
      clearAuth();
      return false;
    } finally {
      refreshFlight = null;
    }
  })();
  return refreshFlight;
}

function authHeader(h: Record<string, string>): Record<string, string> {
  const t = getAccessToken();
  if (t) h.Authorization = `Bearer ${t}`;
  return h;
}

// ---- core fetch helpers -----------------------------------------------------

async function apiFetch<T>(path: string, query?: Record<string, unknown>): Promise<T> {
  const u = url(path, query);
  const run = () =>
    fetch(u, { headers: authHeader({ Accept: 'application/json' }), cache: 'no-store' });

  let res = await run();
  if (res.status === 401) {
    const ok = await tryRefresh();
    if (!ok) throw new AuthRequiredError();
    res = await run();
    if (res.status === 401) { clearAuth(); throw new AuthRequiredError(); }
  }
  if (!res.ok) throw await toError(res, u);
  const text = await res.text();
  return (text ? JSON.parse(text) : null) as T;
}

async function apiPost<T>(path: string, body?: unknown): Promise<T> {
  const u = url(path);
  const run = () => {
    const h = authHeader({ Accept: 'application/json' });
    if (body !== undefined) h['Content-Type'] = 'application/json';
    return fetch(u, { method: 'POST', headers: h, body: body !== undefined ? JSON.stringify(body) : undefined, cache: 'no-store' });
  };

  let res = await run();
  if (res.status === 401) {
    const ok = await tryRefresh();
    if (!ok) throw new AuthRequiredError();
    res = await run();
    if (res.status === 401) { clearAuth(); throw new AuthRequiredError(); }
  }
  if (!res.ok) throw await toError(res, u);
  const text = await res.text();
  return (text ? JSON.parse(text) : null) as T;
}

async function authPost<T>(path: string, body: unknown): Promise<T> {
  const u = url(path);
  const res = await fetch(u, {
    method: 'POST',
    headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
    cache: 'no-store',
  });
  if (!res.ok) throw await toError(res, u);
  const text = await res.text();
  return (text ? JSON.parse(text) : null) as T;
}

// ---- auth endpoints ---------------------------------------------------------

export const staffLogin = (email: string, password: string): Promise<AuthResponse> =>
  authPost<AuthResponse>('/auth/staff/login', { email, password });

const rotateToken = (refreshToken: string): Promise<TokenPair> =>
  authPost<TokenPair>('/auth/refresh', { refreshToken });

export const apiLogout = async (refreshToken: string): Promise<void> => {
  await authPost<null>('/auth/logout', { refreshToken });
};

// ---- delivery endpoints -----------------------------------------------------

/** GET /delivery/orders — defaults to OUT_FOR_DELIVERY queue. */
export function getDeliveryOrders(
  status = 'OUT_FOR_DELIVERY',
  page = 0,
  size = 50,
): Promise<Page<Order>> {
  return apiFetch<Page<Order>>('/delivery/orders', { status, page, size });
}

/** POST /delivery/orders/{id}/deliver — submit OTP to mark the order delivered. */
export function confirmDelivery(orderId: string, otp: string): Promise<Order> {
  return apiPost<Order>(`/delivery/orders/${encodeURIComponent(orderId)}/deliver`, { otp });
}

// ---- push notifications -----------------------------------------------------

/** What the deployment supports: the VAPID key, or `enabled: false` when unset. */
export interface PushConfig {
  enabled: boolean;
  publicKey: string | null;
}

/** The shape `PushSubscription.toJSON()` produces; posted to the API verbatim. */
export interface PushSubscriptionPayload {
  endpoint: string;
  keys: { p256dh: string; auth: string };
}

/**
 * GET /notifications/push-config — public. `enabled: false` means the server has
 * no VAPID keys, so the opt-in is hidden rather than offered and broken.
 */
export function getPushConfig(): Promise<PushConfig> {
  return apiFetch<PushConfig>('/notifications/push-config');
}

/**
 * POST /notifications/subscriptions — register this phone for assignment
 * alerts. Stored against the signed-in rider, which is how the API knows whose
 * device to push a new job to.
 */
export function subscribeToPush(
  subscription: PushSubscriptionPayload,
): Promise<void> {
  return apiPost<void>('/notifications/subscriptions', subscription);
}

/**
 * DELETE /notifications/subscriptions — stop pushing to this phone. Authorised
 * by the endpoint URL itself (an unguessable capability the browser minted), so
 * it needs no token and still works after logout.
 */
export async function unsubscribeFromPush(endpoint: string): Promise<void> {
  await fetch(url('/notifications/subscriptions'), {
    method: 'DELETE',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ endpoint }),
    cache: 'no-store',
  });
}
