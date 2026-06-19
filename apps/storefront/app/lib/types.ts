// Typed models mirroring the public REST API contract (NEXT_PUBLIC_API_BASE_URL).
// These match the Spring `catalog` + `serviceability` module responses for M2.
// NOTE: cost price is intentionally never exposed by the API and so is absent here.

export interface ProductVariant {
  id: string;
  label: string;
  sellingPrice: number; // decimal rupees
  mrp: number | null; // decimal rupees; null when no MRP/strikethrough
  available: boolean;
}

export interface Product {
  id: string;
  name: string;
  slug: string;
  categoryId: string;
  description: string;
  vegMarker: boolean; // true => veg (green dot), false => non-veg (red dot)
  imageUrl: string | null;
  available: boolean;
  variants: ProductVariant[];
}

export interface Category {
  id: string;
  name: string;
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
  label: string; // variant label, e.g. "500 g"
  unitPrice: number; // decimal rupees
  qty: number;
  lineTotal: number; // decimal rupees
  available: boolean; // false => line is out of stock / unbuyable
}

export interface Cart {
  cartId: string;
  items: CartItem[];
  subtotal: number; // decimal rupees
  itemCount: number; // total quantity across lines
}

// ---- Orders (M3) --------------------------------------------------------

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
  deliveryOtp: string;
  placedAt: string; // ISO timestamp
  timeline: OrderTimelineEntry[];
}

export interface PlaceOrderRequest {
  cartId: string;
  customerName: string;
  phone: string;
  address: OrderAddress;
  paymentMethod: PaymentMethod;
}

// ---- Identity (M4) ------------------------------------------------------
// Exact JSON field names per M4_CONTRACT §7. The frontend only ever sees our
// own JWTs + this contract; the auth vendor (Firebase) is hidden behind a
// backend port.

export type UserRole = 'CUSTOMER' | 'STORE_STAFF' | 'ADMIN';

export interface UserDto {
  id: number;
  role: string; // UserRole, kept as string to mirror the API exactly
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
