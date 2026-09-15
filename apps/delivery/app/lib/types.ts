// Types mirroring the delivery REST API contract (/api/v1/delivery/**).

export type OrderStatus =
  | 'PLACED'
  | 'CONFIRMED'
  | 'PACKING'
  | 'OUT_FOR_DELIVERY'
  | 'DELIVERY_FAILED'
  | 'DELIVERED'
  | 'CANCELLED';

export interface OrderItem {
  productName: string;
  label: string;
  unitPrice: number;
  qty: number;
  lineTotal: number;
}

/** One step of an order's status history; `at` is an ISO instant. */
export interface OrderTimelineEntry {
  toStatus: OrderStatus;
  at: string;
  note: string | null;
}

export interface Order {
  /** Internal numeric id — keys the confirm/fail endpoints. */
  id: string;
  /** The short order code the customer has in their app; show this at the door. */
  publicCode: string;
  status: OrderStatus;
  paymentMethod: 'COD' | 'UPI';
  paymentStatus: string;
  customerName: string;
  phone: string;
  address: { line: string; lat: number; lng: number };
  items: OrderItem[];
  subtotal: number;
  total: number;
  placedAt: string;
  /** Status history, oldest first. The DELIVERED entry is when the rider handed it over. */
  timeline: OrderTimelineEntry[];
}

/**
 * GET /delivery/summary — the signed-in rider's own day (store day, IST).
 * `codCollected` is the cash taken at the door on Pay-on-Delivery orders; a UPI
 * order is in `deliveredCount` but never in the money.
 */
export interface DaySummary {
  /** ISO date, e.g. "2026-09-15". */
  date: string;
  deliveredCount: number;
  codOrders: number;
  codCollected: number;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
}

export interface UserDto {
  id: number;
  role: string;
  name: string | null;
  phone: string | null;
  email: string | null;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  user: UserDto;
}

export interface TokenPair {
  accessToken: string;
  refreshToken: string;
}
