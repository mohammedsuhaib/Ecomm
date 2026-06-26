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
