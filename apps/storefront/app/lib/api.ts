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
  AddressInput,
  AuthResponse,
  Cart,
  Category,
  Order,
  Page,
  PlaceOrderRequest,
  Product,
  SavedAddress,
  ServiceabilityResult,
  Store,
  TokenPair,
  UpdateProfileRequest,
  UserDto,
} from './types';
import {
  clearAuth,
  loadAccessToken,
  loadRefreshToken,
  saveTokens,
} from './auth';

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
  // Extra request headers (e.g. Authorization on authenticated GETs).
  headers?: Record<string, string>;
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
    headers: { Accept: 'application/json', ...opts.headers },
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

// Mutating JSON request (POST/PUT/DELETE). Always live (no caching), sends a
// JSON body when provided, and supports extra headers (e.g. Idempotency-Key
// for checkout). Cart/order calls run browser-side; the public base URL is used.
async function apiMutate<T>(
  method: 'POST' | 'PUT' | 'DELETE',
  path: string,
  body?: unknown,
  extraHeaders?: Record<string, string>,
): Promise<T> {
  const url = buildUrl(path);
  const headers: Record<string, string> = { Accept: 'application/json' };
  const init: RequestInit = { method, headers, cache: 'no-store' };
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    init.body = JSON.stringify(body);
  }
  if (extraHeaders) Object.assign(headers, extraHeaders);

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
      /* ignore body read errors */
    }
    throw new ApiError(res.status, url, detail || res.statusText);
  }

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

/** Catalogue sort options accepted by /products and /products/search. */
export type ProductSort = 'name' | 'price_asc' | 'price_desc' | 'discount';

/** Optional product-list filters layered on top of paging. */
export interface ProductListOpts {
  /** When true, request only featured ("Popular picks") products. */
  featured?: boolean;
  /** Server-side sort order; absent => backend default order. */
  sort?: ProductSort;
}

/**
 * GET /products?categoryId=&page=&size=&featured=&sort= — paged products,
 * optionally filtered by category, restricted to featured items, and/or sorted.
 */
export function getProducts(
  categoryId?: string,
  page = 0,
  size = 24,
  listOpts: ProductListOpts = {},
  opts?: FetchOpts,
): Promise<Page<Product>> {
  return apiFetch<Page<Product>>(
    '/products',
    {
      categoryId,
      page,
      size,
      featured: listOpts.featured ? 'true' : undefined,
      sort: listOpts.sort,
    },
    opts,
  );
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

/** GET /products/search?q=&page=&size=&sort= — full-text search results. */
export function searchProducts(
  q: string,
  page = 0,
  size = 24,
  listOpts: Pick<ProductListOpts, 'sort'> = {},
  opts?: FetchOpts,
): Promise<Page<Product>> {
  return apiFetch<Page<Product>>(
    '/products/search',
    { q, page, size, sort: listOpts.sort },
    opts,
  );
}

// ---- Cart (M3, browser-side) --------------------------------------------

/** POST /carts — create a new server-side cart, returns its id. */
export function createCart(): Promise<{ cartId: string }> {
  return apiMutate<{ cartId: string }>('POST', '/carts');
}

/** GET /carts/{cartId} — current cart (live; prices/stock validated server-side). */
export function getCart(cartId: string): Promise<Cart> {
  return apiFetch<Cart>(`/carts/${encodeURIComponent(cartId)}`, undefined, {
    noStore: true,
  });
}

/** POST /carts/{cartId}/items — add a variant; returns the updated cart. */
export function addCartItem(
  cartId: string,
  variantId: string,
  qty: number,
): Promise<Cart> {
  return apiMutate<Cart>(
    'POST',
    `/carts/${encodeURIComponent(cartId)}/items`,
    { variantId, qty },
  );
}

/** PUT /carts/{cartId}/items/{itemId} — set quantity (0 removes the line). */
export function updateCartItem(
  cartId: string,
  itemId: string,
  qty: number,
): Promise<Cart> {
  return apiMutate<Cart>(
    'PUT',
    `/carts/${encodeURIComponent(cartId)}/items/${encodeURIComponent(itemId)}`,
    { qty },
  );
}

/** DELETE /carts/{cartId}/items/{itemId} — remove a line. */
export function removeCartItem(cartId: string, itemId: string): Promise<Cart> {
  return apiMutate<Cart>(
    'DELETE',
    `/carts/${encodeURIComponent(cartId)}/items/${encodeURIComponent(itemId)}`,
  );
}

// ---- Orders (M3, browser-side) ------------------------------------------

/**
 * POST /orders — place an order. The Idempotency-Key header makes retries
 * (flaky mobile networks) safe: the same key returns the original order
 * rather than creating a duplicate (ARCHITECTURE.md §3.5).
 *
 * Ties the order to the logged-in user by sending the access token when a
 * session exists, so the order's user_id is set server-side and it appears in
 * "My orders" / order history. POST /orders is PUBLIC, so an expired token does
 * NOT 401 — it would silently place a GUEST order — therefore we proactively
 * refresh a stale access token first. Guests (no session) place as before.
 */
export async function placeOrder(
  req: PlaceOrderRequest,
  idempotencyKey: string,
): Promise<Order> {
  await ensureFreshAccessToken();
  const access = loadAccessToken();
  const headers: Record<string, string> = { 'Idempotency-Key': idempotencyKey };
  if (access) headers.Authorization = `Bearer ${access}`;
  return apiMutate<Order>('POST', '/orders', req, headers);
}

/** Decode a JWT payload's `exp` (epoch seconds); null if not decodable. */
function jwtExpiry(token: string): number | null {
  const part = token.split('.')[1];
  if (!part) return null;
  try {
    const b64 = part.replace(/-/g, '+').replace(/_/g, '/');
    const padded = b64 + '='.repeat((4 - (b64.length % 4)) % 4);
    const payload = JSON.parse(atob(padded)) as { exp?: number };
    return typeof payload.exp === 'number' ? payload.exp : null;
  } catch {
    return null;
  }
}

/**
 * If a session exists and its access token is expired/near-expiry, rotate it
 * via the refresh token so the following call carries a valid Bearer. No-op for
 * guests; if the refresh token is dead, clear the session and proceed as a
 * guest rather than blocking checkout.
 */
async function ensureFreshAccessToken(): Promise<void> {
  const access = loadAccessToken();
  if (!access) return;
  const exp = jwtExpiry(access);
  const now = Math.floor(Date.now() / 1000);
  if (exp !== null && exp - now > 30) return; // still valid (>30s headroom)
  const refresh = loadRefreshToken();
  if (!refresh) return;
  try {
    saveTokens(await refreshTokens(refresh));
  } catch {
    clearAuth();
  }
}

/** GET /orders/{id} — order summary + status timeline (live). */
export function getOrder(id: string): Promise<Order> {
  return apiFetch<Order>(`/orders/${encodeURIComponent(id)}`, undefined, {
    noStore: true,
  });
}

/**
 * URL for the per-order SSE stream (GET /orders/{id}/stream). Consumed by the
 * tracking page with `new EventSource(...)`, so it must use the public
 * (browser-reachable) base URL.
 */
export function orderStreamUrl(id: string): string {
  return `${API_BASE_URL.replace(/\/$/, '')}/orders/${encodeURIComponent(id)}/stream`;
}

// ---- Auth endpoints (M4, PUBLIC, browser-side) --------------------------

/**
 * POST /auth/phone/verify — exchange a (Firebase) ID token for our own JWTs.
 * In dev the token is `dev:<10-digit phone>` (M4_CONTRACT §2, §9).
 */
export function phoneVerify(firebaseIdToken: string): Promise<AuthResponse> {
  return apiMutate<AuthResponse>('POST', '/auth/phone/verify', {
    firebaseIdToken,
  });
}

/** POST /auth/refresh — rotate: revoke the presented token, issue a new pair. */
export function refreshTokens(refreshToken: string): Promise<TokenPair> {
  return apiMutate<TokenPair>('POST', '/auth/refresh', { refreshToken });
}

/** POST /auth/logout — revoke the refresh token (idempotent → 204). */
export function logout(refreshToken: string): Promise<void> {
  return apiMutate<void>('POST', '/auth/logout', { refreshToken });
}

// ---- Authenticated request helper ---------------------------------------
//
// Injects `Authorization: Bearer <access>` against OUR API base URL only.
// On a 401, attempts exactly ONE rotating refresh then retries the request;
// if the refresh fails, clears the local session and rethrows so the UI can
// redirect to login. Never logs token values.

async function authMutate<T>(
  method: 'POST' | 'PUT' | 'DELETE',
  path: string,
  body?: unknown,
): Promise<T> {
  return authRequest<T>(() => {
    const access = loadAccessToken();
    return apiMutate<T>(
      method,
      path,
      body,
      access ? { Authorization: `Bearer ${access}` } : undefined,
    );
  });
}

async function authGet<T>(
  path: string,
  query?: Record<string, unknown>,
): Promise<T> {
  return authRequest<T>(() => {
    const access = loadAccessToken();
    // GETs that need auth must be live (no caching) and carry the Bearer header.
    return apiFetch<T>(path, query, {
      noStore: true,
      headers: access ? { Authorization: `Bearer ${access}` } : undefined,
    });
  });
}

/** Run an authed call; on 401 do one rotating refresh + retry, else clear+throw. */
async function authRequest<T>(run: () => Promise<T>): Promise<T> {
  try {
    return await run();
  } catch (err) {
    if (!(err instanceof ApiError) || err.status !== 401) throw err;

    const refresh = loadRefreshToken();
    if (!refresh) {
      clearAuth();
      throw err;
    }

    try {
      const pair = await refreshTokens(refresh);
      saveTokens(pair); // persist the rotated pair before retrying
    } catch (refreshErr) {
      // Refresh failed (expired/revoked) — drop the session and surface 401.
      clearAuth();
      throw refreshErr;
    }

    // Retry exactly once with the new access token.
    return run();
  }
}

// ---- Profile + addresses (M4, AUTHENTICATED) ----------------------------

/** GET /me — current user profile. */
export function getMe(): Promise<UserDto> {
  return authGet<UserDto>('/me');
}

/**
 * PUT /me — update the caller's display name. The backend trims and validates
 * length 1..80 (400 otherwise) and returns the refreshed UserDto.
 */
export function updateProfile(name: string): Promise<UserDto> {
  const body: UpdateProfileRequest = { name };
  return authMutate<UserDto>('PUT', '/me', body);
}

/** GET /me/addresses — saved addresses (default first, then newest). */
export function listAddresses(): Promise<SavedAddress[]> {
  return authGet<SavedAddress[]>('/me/addresses');
}

/** POST /me/addresses — add an address; returns the created address. */
export function addAddress(input: AddressInput): Promise<SavedAddress> {
  return authMutate<SavedAddress>('POST', '/me/addresses', input);
}

/** PUT /me/addresses/{id} — update an address; returns the updated address. */
export function updateAddress(
  id: number,
  input: AddressInput,
): Promise<SavedAddress> {
  return authMutate<SavedAddress>(
    'PUT',
    `/me/addresses/${encodeURIComponent(String(id))}`,
    input,
  );
}

/** DELETE /me/addresses/{id} — remove an address. */
export function deleteAddress(id: number): Promise<void> {
  return authMutate<void>(
    'DELETE',
    `/me/addresses/${encodeURIComponent(String(id))}`,
  );
}

// ---- Orders tie-to-user (M4, AUTHENTICATED) -----------------------------

/** GET /orders/mine?page=&size= — the caller's orders, newest first. */
export function getMyOrders(page = 0, size = 20): Promise<Page<Order>> {
  return authGet<Page<Order>>('/orders/mine', { page, size });
}

/** POST /orders/{id}/reorder — new cart populated from the order. */
export function reorder(orderId: string): Promise<Cart> {
  return authMutate<Cart>(
    'POST',
    `/orders/${encodeURIComponent(orderId)}/reorder`,
  );
}

// ---- Cart merge on login (M4, AUTHENTICATED) ----------------------------

/**
 * POST /carts/{cartId}/merge — merge the guest cart into the caller's active
 * cart. The returned cart's `cartId` may differ; callers MUST store it.
 */
export function mergeCart(cartId: string): Promise<Cart> {
  return authMutate<Cart>(
    'POST',
    `/carts/${encodeURIComponent(cartId)}/merge`,
  );
}
