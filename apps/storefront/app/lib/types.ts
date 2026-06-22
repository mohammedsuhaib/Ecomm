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
  trackingToken: string; // unguessable token used to fetch/track this order
  status: OrderStatus;
  paymentMethod: PaymentMethod;
  paymentStatus: string;
  customerName: string;
  phone: string;
  address: OrderAddress;
  items: OrderItem[];
  subtotal: number;
  total: number;
  deliveryOtp: string | null; // present only while OUT_FOR_DELIVERY
  placedAt: string; // ISO timestamp
  timeline: OrderTimelineEntry[];
}

export interface PlaceOrderRequest {
  cartId: string;
  customerName: string;
  phone: string;
  address: OrderAddress;
  paymentMethod: PaymentMethod;
  expectedTotal: number; // the subtotal the customer confirmed; server rejects a mismatch
}
