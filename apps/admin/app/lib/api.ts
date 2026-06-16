// Typed fetch client for the Town Basket admin REST API.
//
// Reuses the storefront's conventions: a thin `apiFetch` wrapper that resolves
// URLs per execution context (server/browser), maps non-2xx to a typed
// `ApiError`, and a separate `apiMutate` for POSTing status transitions. The
// admin queue's live updates use EventSource against the public base URL.
//
// NOTE: admin is NOT secured yet (auth is M4) — no Authorization header here.

import type { Order, Page, TransitionRequest } from './types';

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

async function apiFetch<T>(
  path: string,
  query?: Record<string, unknown>,
): Promise<T> {
  const url = buildUrl(path, query);
  let res: Response;
  try {
    res = await fetch(url, {
      headers: { Accept: 'application/json' },
      cache: 'no-store',
    });
  } catch (cause) {
    throw new ApiError(0, url, `Network error reaching API: ${String(cause)}`);
  }
  if (!res.ok) {
    let detail = '';
    try {
      detail = await res.text();
    } catch {
      /* ignore */
    }
    throw new ApiError(res.status, url, detail || res.statusText);
  }
  const text = await res.text();
  return (text ? JSON.parse(text) : null) as T;
}

async function apiMutate<T>(
  method: 'POST' | 'PUT' | 'DELETE',
  path: string,
  body?: unknown,
): Promise<T> {
  const url = buildUrl(path);
  const headers: Record<string, string> = { Accept: 'application/json' };
  const init: RequestInit = { method, headers, cache: 'no-store' };
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    init.body = JSON.stringify(body);
  }
  let res: Response;
  try {
    res = await fetch(url, init);
  } catch (cause) {
    throw new ApiError(0, url, `Network error reaching API: ${String(cause)}`);
  }
  if (!res.ok) {
    let detail = '';
    try {
      detail = await res.text();
    } catch {
      /* ignore */
    }
    throw new ApiError(res.status, url, detail || res.statusText);
  }
  const text = await res.text();
  return (text ? JSON.parse(text) : null) as T;
}

// ---- Endpoint functions -------------------------------------------------

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
 */
export function adminOrderStreamUrl(): string {
  return `${API_BASE_URL.replace(/\/$/, '')}/admin/orders/stream`;
}
