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
  deliveryOtp: string;
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
