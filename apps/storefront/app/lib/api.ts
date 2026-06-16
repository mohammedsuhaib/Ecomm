// Typed fetch client for the Town Basket public REST API.
//
// All calls go through `apiFetch`, a thin wrapper that:
//  - resolves URLs against NEXT_PUBLIC_API_BASE_URL (default localhost:8080/api/v1),
//  - applies a sensible Next.js cache strategy (catalogue reads are cacheable
//    and revalidated; serviceability is always live),
//  - turns non-2xx responses into a typed `ApiError`, with 404 surfaced so
//    callers (e.g. product detail) can render notFound().
//
// The frontend never knows the backend's internal module topology — it only
// speaks this contract (ARCHITECTURE.md §4.1, §6).

import type {
  Category,
  Page,
  Product,
  ServiceabilityResult,
  Store,
} from './types';

// Resolve the API base URL per execution context:
//  - Browser: the publicly reachable URL (NEXT_PUBLIC_API_BASE_URL).
//  - Server (SSR / inside Docker): an internal URL when provided
//    (INTERNAL_API_BASE_URL, e.g. http://api:8080/api/v1 under docker-compose),
//    because "localhost" on the server refers to the frontend container itself,
//    not the API container.
const PUBLIC_API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080/api/v1';

export function getApiBaseUrl(): string {
  if (typeof window === 'undefined') {
    return process.env.INTERNAL_API_BASE_URL ?? PUBLIC_API_BASE_URL;
  }
  return PUBLIC_API_BASE_URL;
}

// Public (browser) base URL — also referenced by the service worker.
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

type FetchOpts = {
  // Next.js fetch caching: pass `revalidate` (ISR seconds) or `noStore`.
  revalidate?: number | false;
  noStore?: boolean;
  signal?: AbortSignal;
};

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
  opts: FetchOpts = {},
): Promise<T> {
  const url = buildUrl(path, query);

  // Translate our caching intent into the Next fetch options. `no-store`
  // for live data; otherwise an ISR window so hot catalogue pages stay fast.
  const next: { revalidate?: number | false } = {};
  const init: RequestInit & { next?: typeof next } = {
    headers: { Accept: 'application/json' },
    signal: opts.signal,
  };
  if (opts.noStore) {
    init.cache = 'no-store';
  } else if (opts.revalidate !== undefined) {
    next.revalidate = opts.revalidate;
    init.next = next;
  } else {
    next.revalidate = 60; // default: revalidate catalogue reads every 60s
    init.next = next;
  }

  let res: Response;
  try {
    res = await fetch(url, init);
  } catch (cause) {
    // Network/DNS failure — surface as a 0-status ApiError so the UI can
    // degrade gracefully (e.g. show the offline fallback / retry state).
    throw new ApiError(0, url, `Network error reaching API: ${String(cause)}`);
  }

  if (!res.ok) {
    let detail = '';
    try {
      detail = await res.text();
    } catch {
      /* ignore body read errors */
    }
    throw new ApiError(res.status, url, detail || res.statusText);
  }

  // 204/empty bodies shouldn't happen for these GETs, but be defensive.
  const text = await res.text();
  return (text ? JSON.parse(text) : null) as T;
}

// ---- Endpoint functions -------------------------------------------------

/** GET /store — store profile, hours, radius, min order value. */
export function getStore(opts?: FetchOpts): Promise<Store> {
  return apiFetch<Store>('/store', undefined, opts);
}

/**
 * GET /serviceability/check?lat=&lng= — always live (no caching), since the
 * answer depends on the caller's coordinates and store config.
 */
export function checkServiceability(
  lat: number,
  lng: number,
  opts?: FetchOpts,
): Promise<ServiceabilityResult> {
  return apiFetch<ServiceabilityResult>(
    '/serviceability/check',
    { lat, lng },
    { noStore: true, ...opts },
  );
}

/** GET /categories — ordered category list for the catalogue nav. */
export function getCategories(opts?: FetchOpts): Promise<Category[]> {
  return apiFetch<Category[]>('/categories', undefined, opts);
}

/** GET /products?categoryId=&page=&size= — paged products, optionally filtered. */
export function getProducts(
  categoryId?: string,
  page = 0,
  size = 24,
  opts?: FetchOpts,
): Promise<Page<Product>> {
  return apiFetch<Page<Product>>('/products', { categoryId, page, size }, opts);
}

/**
 * GET /products/{idOrSlug} — single product. Returns null on 404 so detail
 * pages can call notFound() without try/catch noise; other errors propagate.
 */
export async function getProduct(
  idOrSlug: string,
  opts?: FetchOpts,
): Promise<Product | null> {
  try {
    return await apiFetch<Product>(
      `/products/${encodeURIComponent(idOrSlug)}`,
      undefined,
      opts,
    );
  } catch (err) {
    if (err instanceof ApiError && err.status === 404) return null;
    throw err;
  }
}

/** GET /products/search?q=&page=&size= — full-text search results. */
export function searchProducts(
  q: string,
  page = 0,
  size = 24,
  opts?: FetchOpts,
): Promise<Page<Product>> {
  return apiFetch<Page<Product>>('/products/search', { q, page, size }, opts);
}
