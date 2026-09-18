// Typed models mirroring the admin REST API contract (NEXT_PUBLIC_API_BASE_URL).
// These match the Spring `orders` module responses used by the admin queue (M3).

export type PaymentMethod = 'COD' | 'UPI';

export type OrderStatus =
  | 'PLACED'
  | 'CONFIRMED'
  | 'PACKING'
  | 'READY_FOR_DELIVERY'
  | 'OUT_FOR_DELIVERY'
  | 'DELIVERY_FAILED'
  | 'DELIVERED'
  | 'CANCELLED';

export interface OrderAddress {
  line: string;
  lat: number;
  lng: number;
}

export interface OrderItem {
  productName: string;
  label: string;
  unitPrice: number;
  qty: number;
  lineTotal: number;
}

export interface OrderTimelineEntry {
  toStatus: OrderStatus;
  at: string; // ISO timestamp
  // Reason recorded with the step (failed delivery, staff cancel). NOT an
  // internal note: the customer's own order page renders it verbatim for both
  // of those steps, so treat anything written here as customer-facing.
  note?: string | null;
}

export interface Order {
  /** Internal numeric id — keys the admin transition/assign endpoints. */
  id: string;
  /**
   * The short order code the customer quotes (e.g. "7K4M2QX9"). Show this on
   * the queue so staff and customer are talking about the same string; the
   * search box matches it (and tolerates O-for-0 style mistakes).
   */
  publicCode: string;
  status: OrderStatus;
  paymentMethod: PaymentMethod;
  paymentStatus: string;
  customerName: string;
  phone: string;
  address: OrderAddress;
  items: OrderItem[];
  subtotal: number;
  total: number;
  // Never sent on the admin surface — staff collect the code from the customer
  // at handover and type it into the DELIVERED prompt.
  deliveryOtp: string | null;
  placedAt: string; // ISO timestamp
  timeline: OrderTimelineEntry[];
  // identity.users id of the assigned delivery agent; null = unassigned (pool).
  assignedAgentId: number | null;
  // The rider's live position is a CUSTOMER field: it is set only on the
  // customer's own tracking read, never on the admin surface. Always null here.
  riderLocation: { lat: number; lng: number; recordedAt: string } | null;
}

/** A delivery agent that an order can be dispatched to. */
export interface DeliveryAgent {
  id: number;
  name: string | null;
  email: string | null;
  active: boolean;
  /** Rider's own switch — false means "no new assignments"; optional until every API instance has V2_6. */
  onDuty?: boolean;
}

/** Outcome of a CSV product import (rows are 1-based file line numbers). */
export interface ProductImportResult {
  created: number;
  skipped: number;
  errors: { row: number; message: string }[];
}

/** One curated HSN entry with its candidate GST rates (qualifier-dependent). */
export interface HsnSuggestion {
  hsn: string;
  description: string;
  options: { ratePercent: number; qualifier: string }[];
}

/** GST-rate prefill for an HSN: the catalog's own rate + curated candidates. */
export interface HsnRateSuggestions {
  catalogRate: number | null;
  suggestions: HsnSuggestion[];
}

/** Delivered-order count and summed order value for one agent on one date (yyyy-mm-dd). */
export interface AgentDeliveryStat {
  agentId: number;
  date: string;
  deliveries: number;
  /** Sum of the delivered orders' totals in rupees, tax-inclusive. */
  amount: number;
}

// Spring Data style page envelope used by list endpoints.
export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
}

export interface TransitionRequest {
  to: OrderStatus;
  deliveryOtp?: string;
  reason?: string;
}

// ---- M4 Identity (auth) — see M4_CONTRACT.md §7 ----------------------------

/** The authenticated user/staff record (contract §7). */
export interface UserDto {
  id: number;
  role: string;
  name: string | null;
  phone: string | null;
  email: string | null;
}

/** Returned by POST /auth/staff/login (and /auth/phone/verify). */
export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  user: UserDto;
}

/** Returned by POST /auth/refresh — a rotated access+refresh pair (no user). */
export interface TokenPair {
  accessToken: string;
  refreshToken: string;
}

// ---- Catalogue management (admin) — see API contract /admin/catalog --------

/**
 * A purchasable variant of a product (e.g. "500 g", "1 L"). `costPrice` is
 * admin-only (never exposed on the storefront); the catalogue UI uses it to
 * surface margin context to staff.
 */
export interface AdminVariant {
  id: number;
  label: string;
  sellingPrice: number;
  costPrice: number;
  mrp: number | null;
  available: boolean;
  sortOrder: number;
}

/**
 * A catalogue product as seen by staff — includes UNAVAILABLE items and the
 * full variant list with cost prices. `nameKn` is the Kannada name (auto-filled
 * by transliteration on the backend when left blank).
 */
export interface AdminProduct {
  id: number;
  name: string;
  nameKn: string | null;
  slug: string;
  categoryId: number;
  categoryName: string;
  description: string | null;
  vegMarker: boolean;
  imageUrl: string | null;
  available: boolean;
  featured: boolean;
  /** HSN classification printed on GST invoices; null until staff fill it in. */
  hsnCode: string | null;
  /** GST slab % (0/5/18/40). Prices are tax-inclusive, so this never changes them. */
  gstRatePercent: number;
  variants: AdminVariant[];
}

/** A catalogue category. */
export interface Category {
  id: number;
  name: string;
  slug: string;
  imageUrl: string | null;
  sortOrder: number;
}

// ---- Analytics (admin) -------------------------------------------------------

/** Today's GMV, order counts, and pending queue depth. */
export interface AnalyticsSummary {
  todayRevenue: number;
  todayOrders: number;
  todayDelivered: number;
  pendingOrders: number;
  // NOTE: no range fields here on purpose — everything spanning the dashboard's
  // 7/30/90-day filter is summed from the daily series, which the filter drives.
}

/** Revenue, order count, and gross profit for a single calendar day. */
export interface DailySummary {
  date: string; // yyyy-MM-dd
  revenue: number;
  orders: number;
  grossProfit: number;
}

/** Top-selling variant for the analytics period. */
export interface TopProduct {
  productName: string;
  variantLabel: string;
  totalQty: number;
  totalRevenue: number;
}

/** A variant at or below its low-stock threshold. */
export interface LowStockItem {
  variantId: number;
  productId: number;
  productName: string;
  variantLabel: string;
  available: number;
  threshold: number;
}

// ---- Admin Inventory --------------------------------------------------------

/** Stock level for one variant — admin view includes product/variant names. */
export interface StockLevel {
  id: number;
  variantId: number;
  productId: number;
  productName: string;
  variantLabel: string;
  sellingPrice: number;
  onHand: number;
  reserved: number;
  available: number;
  lowStockThreshold: number;
}

export interface StockCorrectionRequest {
  newOnHand: number;
  reason: string;
}

// ---- Store settings (serviceability, admin) --------------------------------

/** GET /admin/store — the store card, incl. live open state and any manual closure. */
export interface StoreSettings {
  name: string;
  address: string;
  openingTime: string; // "HH:mm[:ss]" store-local
  closingTime: string;
  deliveryRadiusMeters: number;
  minOrderValue: number;
  lat: number;
  lng: number;
  open: boolean; // serving right now, on the SERVER clock
  opensNextDay: boolean;
  /** Public contact number shown to customers; null until it is set here. */
  supportPhone: string | null;
  manuallyClosed: boolean; // a "closed for today" is in force
  closedReason: string | null;
  closedUntil: string | null; // ISO instant when the closure lapses
}

/** PUT /admin/store body — the whole card, always. */
export interface StoreUpdateRequest {
  name: string;
  address: string;
  lat: number;
  lng: number;
  deliveryRadiusMeters: number;
  openingTime: string; // "HH:mm"
  closingTime: string;
  minOrderValue: number;
  /** Blank clears it, and the storefront then stops offering a way to call. */
  supportPhone: string;
}

/** GET /admin/staff — password-login accounts (ADMIN + STORE_STAFF). ADMIN only. */
export interface StaffMember {
  id: number;
  name: string | null;
  email: string | null;
  role: 'ADMIN' | 'STORE_STAFF' | string;
  active: boolean;
}
