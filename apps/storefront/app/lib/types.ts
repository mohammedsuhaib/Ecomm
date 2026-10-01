// Typed models mirroring the public REST API contract (NEXT_PUBLIC_API_BASE_URL).
// These match the Spring `catalog` + `serviceability` module responses for M2.
// NOTE: cost price is intentionally never exposed by the API and so is absent here.

export interface ProductVariant {
  id: string;
  label: string;
  sellingPrice: number; // decimal rupees
  mrp: number | null; // decimal rupees; null when no MRP/strikethrough
  available: boolean; // store's manual on/off toggle
  availableStock: number; // live sellable units (on_hand - reserved); 0 => out of stock
}

export interface Product {
  id: string;
  name: string;
  nameKn?: string | null; // Kannada transliteration (catalog name_kn); null until backfilled
  slug: string;
  categoryId: string;
  description: string;
  vegMarker: boolean; // true => veg (green dot), false => non-veg (red dot)
  imageUrl: string | null;
  available: boolean;
  featured?: boolean; // true => surfaced in "Popular picks" (catalog ?featured=true)
  variants: ProductVariant[];
}

export interface Category {
  id: string;
  name: string;
  nameKn?: string | null; // Kannada name (catalog name_kn); null until filled — see lib/productName.ts
  slug: string;
  imageUrl: string | null;
  sortOrder: number;
}

export interface Store {
  name: string;
  address: string;
  openingTime: string;
  closingTime: string;
  deliveryRadiusMeters: number;
  minOrderValue: number;
  lat: number;
  lng: number;
  /**
   * The store's public contact number, or null/absent when staff haven't set
   * one. Copy that asks a customer to get in touch is shown only when this is
   * present — there is nowhere to send them otherwise.
   */
  supportPhone?: string | null;
  /**
   * The store's GST registration number, null until it is registered. Public
   * by law (it is displayed at the place of business and on every invoice);
   * carried here so the storefront can show it if it ever needs to.
   */
  gstin?: string | null;
  /** Whether the store is serving right now, decided on the SERVER clock. */
  open: boolean;
  /** True when the next opening is the following day (today's window has closed). */
  opensNextDay: boolean;
  /** A staff "closed for today" is in force — the reason `open` is false right now. */
  manuallyClosed?: boolean;
  /** Staff's reason for that closure, shown to customers; null/absent otherwise. */
  closedReason?: string | null;
}

export interface ServiceabilityResult {
  serviceable: boolean;
  distanceMeters: number;
  radiusMeters: number;
  storeName: string;
}

// Spring Data style page envelope used by list endpoints.
export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
}

// ---- Cart (M3) ----------------------------------------------------------

export interface CartItem {
  itemId: string;
  variantId: string;
  productId: string;
  productName: string;
  productNameKn?: string | null; // Kannada name; null until backfilled — see lib/productName.ts
  label: string; // variant label, e.g. "500 g"
  unitPrice: number; // decimal rupees
  qty: number;
  lineTotal: number; // decimal rupees
  available: boolean; // false => the item has been marked unavailable by the store
  availableStock: number; // live sellable units (on_hand - reserved)
}

export interface Cart {
  cartId: string;
  items: CartItem[];
  subtotal: number; // decimal rupees
  itemCount: number; // total quantity across lines
  checkedOut: boolean; // true once this cart has been turned into an order
}

// ---- Orders (M3) --------------------------------------------------------

export type PaymentMethod = 'COD' | 'UPI';

/** Which methods this deployment accepts — the UI renders exactly these. */
export interface PaymentMethods {
  methods: PaymentMethod[];
}

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
  // Kannada name as it stood at the sale. Null for orders placed before the
  // snapshot column existed, and for products not yet transliterated.
  productNameKn?: string | null;
  label: string;
  unitPrice: number;
  qty: number;
  lineTotal: number;
}

export interface OrderTimelineEntry {
  toStatus: OrderStatus;
  at: string; // ISO timestamp
  note?: string | null; // reason recorded with the step (e.g. why a delivery failed)
}

/**
 * Where the rider is right now. `recordedAt` is when the server accepted the
 * fix (store clock), so the page can say how old it is; the server already
 * hides fixes older than a few minutes, so a value here is recent.
 */
export interface RiderLocation {
  lat: number;
  lng: number;
  recordedAt: string; // ISO timestamp
}

export interface Order {
  /** Internal numeric id — keys the SSE stream and reorder. Never shown to customers. */
  id: string;
  trackingToken: string; // unguessable token used to fetch/track this order
  /**
   * The short, speakable order number the customer quotes (Crockford base32,
   * e.g. "7K4M2QX9"). Show THIS, not `id`: the id is sequential, so displaying
   * it publishes the store's order volume.
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
  /** GST already contained in `total` (prices are tax-inclusive); informational. */
  totalTax: number;
  deliveryOtp: string | null; // present only while OUT_FOR_DELIVERY
  /**
   * The assigned rider's live position. Set ONLY on the single-order tracking
   * read (getOrder), only while OUT_FOR_DELIVERY, and only while the rider's
   * last fix is fresh — null otherwise, and always null on the order history
   * list. Same gate as deliveryOtp, for the same reason: it is this customer's
   * to see only while the rider is driving to them.
   */
  riderLocation: RiderLocation | null;
  placedAt: string; // ISO timestamp
  timeline: OrderTimelineEntry[];
  /**
   * GST invoice number from the per-financial-year series (e.g. "TB/25-26/00001"),
   * and when it was issued. Both null until an invoice is actually issued —
   * downloading one assigns it.
   */
  invoiceNumber: string | null;
  invoicedAt: string | null; // ISO timestamp
}

export interface PlaceOrderRequest {
  cartId: string;
  customerName: string;
  phone: string;
  address: OrderAddress;
  paymentMethod: PaymentMethod;
  expectedTotal: number; // the subtotal the customer confirmed; server rejects a mismatch
}

// ---- Identity (M4) ------------------------------------------------------
// Exact JSON field names per M4_CONTRACT §7. The frontend only ever sees our
// own JWTs + this contract; the auth vendor (Firebase) is hidden behind a
// backend port.

export interface UserDto {
  id: number;
  role: string; // 'CUSTOMER' | 'STORE_STAFF' | 'ADMIN', kept as string to mirror the API exactly
  name: string | null;
  phone: string | null;
  email: string | null;
}

/** PUT /me body — update the caller's display name (trimmed length 1..80). */
export interface UpdateProfileRequest {
  name: string;
}

/** Issued by POST /auth/phone/verify (login/signup). */
export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  user: UserDto;
}

/** Issued by POST /auth/refresh (rotating). */
export interface TokenPair {
  accessToken: string;
  refreshToken: string;
}

/** GET /me/addresses item (= backend SavedAddressDto). */
export interface SavedAddress {
  id: number;
  label: string | null;
  line: string;
  lat: number;
  lng: number;
  isDefault: boolean;
}

/** POST/PUT /me/addresses body. */
export interface AddressInput {
  label?: string | null;
  line: string;
  lat: number;
  lng: number;
  isDefault?: boolean;
}
