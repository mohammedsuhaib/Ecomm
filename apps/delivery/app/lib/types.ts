// Types mirroring the delivery REST API contract (/api/v1/delivery/**).

export type OrderStatus =
  | 'PLACED'
  | 'CONFIRMED'
  | 'PACKING'
  | 'OUT_FOR_DELIVERY'
  | 'DELIVERED'
  | 'CANCELLED';

export interface OrderItem {
  productName: string;
  label: string;
  unitPrice: number;
  qty: number;
  lineTotal: number;
}

export interface Order {
  id: string;
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
