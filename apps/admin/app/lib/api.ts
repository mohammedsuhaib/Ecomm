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
  AdminProduct,
  AdminVariant,
  AnalyticsSummary,
  AuthResponse,
  Category,
  DailySummary,
  LowStockItem,
  Order,
  Page,
  StockCorrectionRequest,
  StockLevel,
  TokenPair,
  TopProduct,
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

// ---- Account (M4 hardening: staff password change) -------------------------

/**
 * POST /me/password — change the signed-in staffer's password (contract §3).
 * Sends the Bearer token via the authed `apiMutate`; the backend BCrypt-verifies
 * `currentPassword` and stores `newPassword`. Resolves on 204 (no content); the
 * session's existing tokens stay valid, so there is no re-login.
 *
 * Error mapping is left to the caller (422 → wrong/unsupported account, 400 →
 * weak new password) so the UI can surface friendly messages.
 */
export async function changePassword(
  currentPassword: string,
  newPassword: string,
): Promise<void> {
  await apiMutate<null>('POST', '/me/password', {
    currentPassword,
    newPassword,
  });
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

// ---- Catalogue management (admin, contract /admin/catalog) ------------------
//
// Categories + products + variants. List endpoints return the full catalogue
// INCLUDING unavailable items (the storefront filters those out; staff need to
// see and toggle them). The base path is `/admin/catalog`.

const CATALOG_BASE = '/admin/catalog';

/** Request body for creating a category. */
export interface CategoryCreateRequest {
  name: string;
  slug?: string;
  sortOrder?: number;
  imageUrl?: string | null;
}

/** Request body for updating a category (slug is immutable after create). */
export interface CategoryUpdateRequest {
  name: string;
  sortOrder?: number;
  imageUrl?: string | null;
}

/** A variant payload sent when creating a product (inline) or a variant. */
export interface VariantWriteRequest {
  label: string;
  sellingPrice: number;
  costPrice: number;
  mrp?: number | null;
  available: boolean;
  sortOrder: number;
}

/** Request body for creating a product (variants sent inline). */
export interface ProductCreateRequest {
  name: string;
  nameKn?: string | null;
  slug?: string;
  categoryId: number;
  description?: string | null;
  vegMarker: boolean;
  imageUrl?: string | null;
  available: boolean;
  featured: boolean;
  variants: VariantWriteRequest[];
}

/** Request body for updating a product's own fields (variants managed separately). */
export interface ProductUpdateRequest {
  name: string;
  nameKn?: string | null;
  categoryId: number;
  description?: string | null;
  vegMarker: boolean;
  imageUrl?: string | null;
  available: boolean;
  featured: boolean;
}

// -- Categories --------------------------------------------------------------

/** GET /admin/catalog/categories — all categories, ordered by sortOrder. */
export function getAdminCategories(): Promise<Category[]> {
  return apiFetch<Category[]>(`${CATALOG_BASE}/categories`);
}

/** POST /admin/catalog/categories — create a category. */
export function createCategory(req: CategoryCreateRequest): Promise<Category> {
  return apiMutate<Category>('POST', `${CATALOG_BASE}/categories`, req);
}

/** PUT /admin/catalog/categories/{id} — rename / re-sort / re-image. */
export function updateCategory(
  id: number,
  req: CategoryUpdateRequest,
): Promise<Category> {
  return apiMutate<Category>('PUT', `${CATALOG_BASE}/categories/${id}`, req);
}

/**
 * DELETE /admin/catalog/categories/{id} — remove an empty category. The backend
 * returns 422 (ApiError) when the category still has products; the caller maps
 * that to a friendly "move/remove its products first" message.
 */
export function deleteCategory(id: number): Promise<null> {
  return apiMutate<null>('DELETE', `${CATALOG_BASE}/categories/${id}`);
}

// -- Products ----------------------------------------------------------------

/**
 * GET /admin/catalog/products — paged product list (includes unavailable).
 * Optional `categoryId` / `q` filters; defaults to page 0, size 50.
 */
export function getAdminProducts(opts?: {
  categoryId?: number;
  q?: string;
  page?: number;
  size?: number;
}): Promise<Page<AdminProduct>> {
  return apiFetch<Page<AdminProduct>>(`${CATALOG_BASE}/products`, {
    categoryId: opts?.categoryId,
    q: opts?.q,
    page: opts?.page ?? 0,
    size: opts?.size ?? 50,
  });
}

/** GET /admin/catalog/products/{id} — one product with its variants. */
export function getAdminProduct(id: number): Promise<AdminProduct> {
  return apiFetch<AdminProduct>(`${CATALOG_BASE}/products/${id}`);
}

/** POST /admin/catalog/products — create a product with inline variants. */
export function createProduct(req: ProductCreateRequest): Promise<AdminProduct> {
  return apiMutate<AdminProduct>('POST', `${CATALOG_BASE}/products`, req);
}

/** PUT /admin/catalog/products/{id} — update a product's own fields. */
export function updateProduct(
  id: number,
  req: ProductUpdateRequest,
): Promise<AdminProduct> {
  return apiMutate<AdminProduct>('PUT', `${CATALOG_BASE}/products/${id}`, req);
}

/** POST /admin/catalog/products/{id}/availability — toggle product visibility. */
export function setProductAvailability(
  id: number,
  available: boolean,
): Promise<AdminProduct> {
  return apiMutate<AdminProduct>(
    'POST',
    `${CATALOG_BASE}/products/${id}/availability`,
    { available },
  );
}

/** DELETE /admin/catalog/products/{id} — permanently remove a product. */
export function deleteProduct(id: number): Promise<null> {
  return apiMutate<null>('DELETE', `${CATALOG_BASE}/products/${id}`);
}

// -- Variants ----------------------------------------------------------------

/** POST /admin/catalog/products/{id}/variants — add a variant to a product. */
export function createVariant(
  productId: number,
  req: VariantWriteRequest,
): Promise<AdminVariant> {
  return apiMutate<AdminVariant>(
    'POST',
    `${CATALOG_BASE}/products/${productId}/variants`,
    req,
  );
}

/** PUT /admin/catalog/products/{id}/variants/{variantId} — update a variant. */
export function updateVariant(
  productId: number,
  variantId: number,
  req: VariantWriteRequest,
): Promise<AdminVariant> {
  return apiMutate<AdminVariant>(
    'PUT',
    `${CATALOG_BASE}/products/${productId}/variants/${variantId}`,
    req,
  );
}

/**
 * POST /admin/catalog/products/{id}/variants/{variantId}/availability — toggle
 * a single variant's availability.
 */
export function setVariantAvailability(
  productId: number,
  variantId: number,
  available: boolean,
): Promise<AdminVariant> {
  return apiMutate<AdminVariant>(
    'POST',
    `${CATALOG_BASE}/products/${productId}/variants/${variantId}/availability`,
    { available },
  );
}

/** DELETE /admin/catalog/products/{id}/variants/{variantId} — remove a variant. */
export function deleteVariant(
  productId: number,
  variantId: number,
): Promise<null> {
  return apiMutate<null>(
    'DELETE',
    `${CATALOG_BASE}/products/${productId}/variants/${variantId}`,
  );
}

// ---- Analytics (admin) -----------------------------------------------------

/** GET /admin/analytics/summary — today's GMV, order counts, pending queue. */
export function getAnalyticsSummary(storeId = 1): Promise<AnalyticsSummary> {
  return apiFetch<AnalyticsSummary>('/admin/analytics/summary', { storeId });
}

/** GET /admin/analytics/daily — daily revenue + gross profit for last N days. */
export function getDailySummary(storeId = 1, days = 30): Promise<DailySummary[]> {
  return apiFetch<DailySummary[]>('/admin/analytics/daily', { storeId, days });
}

/** GET /admin/analytics/top-products — top-selling variants by qty. */
export function getTopProducts(
  storeId = 1,
  days = 30,
  limit = 10,
): Promise<TopProduct[]> {
  return apiFetch<TopProduct[]>('/admin/analytics/top-products', { storeId, days, limit });
}

/** GET /admin/analytics/low-stock — variants at or below their low-stock threshold. */
export function getLowStockItems(storeId = 1): Promise<LowStockItem[]> {
  return apiFetch<LowStockItem[]>('/admin/analytics/low-stock', { storeId });
}

// ---- Admin Inventory --------------------------------------------------------

/** GET /admin/inventory/stock — paged stock levels with product names. */
export function getStockLevels(
  storeId = 1,
  page = 0,
  size = 100,
): Promise<Page<StockLevel>> {
  return apiFetch<Page<StockLevel>>('/admin/inventory/stock', { storeId, page, size });
}

/** POST /admin/inventory/stock/{variantId}/correction — set on_hand to absolute value. */
export async function correctStock(
  variantId: number,
  req: StockCorrectionRequest,
  storeId = 1,
): Promise<void> {
  await apiMutate<null>(
    'POST',
    `/admin/inventory/stock/${encodeURIComponent(variantId)}/correction?storeId=${storeId}`,
    req,
  );
}
