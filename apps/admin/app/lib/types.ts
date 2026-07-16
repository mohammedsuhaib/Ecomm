// Typed models mirroring the admin REST API contract (NEXT_PUBLIC_API_BASE_URL).
// These match the Spring `orders` module responses used by the admin queue (M3).

export type PaymentMethod = 'COD' | 'UPI';

export type OrderStatus =
  | 'PLACED'
  | 'CONFIRMED'
  | 'PACKING'
  | 'OUT_FOR_DELIVERY'
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
}

export interface Order {
  id: string;
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
}

/** A delivery agent that an order can be dispatched to. */
export interface DeliveryAgent {
  id: number;
  role: string;
  name: string | null;
  phone: string | null;
  email: string | null;
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
  weekRevenue: number;
  weekOrders: number;
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
