import type { OrderStatus } from './types';

export const STATUS_LABELS: Record<OrderStatus, string> = {
  PLACED: 'Placed',
  CONFIRMED: 'Confirmed',
  PACKING: 'Packing',
  OUT_FOR_DELIVERY: 'Out for delivery',
  DELIVERED: 'Delivered',
  CANCELLED: 'Cancelled',
};

// The happy-path forward flow (CANCELLED is reachable from any active state).
const FLOW: OrderStatus[] = [
  'PLACED',
  'CONFIRMED',
  'PACKING',
  'OUT_FOR_DELIVERY',
  'DELIVERED',
];

/** The single forward transition available from a status (null if terminal). */
export function nextStatus(status: OrderStatus): OrderStatus | null {
  const i = FLOW.indexOf(status);
  if (i < 0 || i >= FLOW.length - 1) return null;
  return FLOW[i + 1];
}

/** Whether an active order can still be cancelled (not delivered/cancelled). */
export function canCancel(status: OrderStatus): boolean {
  return status !== 'DELIVERED' && status !== 'CANCELLED';
}

// Status filter tabs for the queue header. "" => all.
export const STATUS_TABS: { value: string; label: string }[] = [
  { value: '', label: 'All' },
  { value: 'PLACED', label: 'Placed' },
  { value: 'CONFIRMED', label: 'Confirmed' },
  { value: 'PACKING', label: 'Packing' },
  { value: 'OUT_FOR_DELIVERY', label: 'Out for delivery' },
  { value: 'DELIVERED', label: 'Delivered' },
  { value: 'CANCELLED', label: 'Cancelled' },
];
