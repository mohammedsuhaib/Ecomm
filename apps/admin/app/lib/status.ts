import type { Order, OrderStatus } from './types';

export const STATUS_LABELS: Record<OrderStatus, string> = {
  PLACED: 'Placed',
  CONFIRMED: 'Confirmed',
  PACKING: 'Packing',
  READY_FOR_DELIVERY: 'Ready for delivery',
  OUT_FOR_DELIVERY: 'Out for delivery',
  DELIVERY_FAILED: 'Delivery failed',
  DELIVERED: 'Delivered',
  CANCELLED: 'Cancelled',
};

// The happy-path forward flow (CANCELLED is reachable from any active state).
const FLOW: OrderStatus[] = [
  'PLACED',
  'CONFIRMED',
  'PACKING',
  'READY_FOR_DELIVERY',
  'OUT_FOR_DELIVERY',
  'DELIVERED',
];

/** The single forward transition available from a status (null if terminal). */
export function nextStatus(status: OrderStatus): OrderStatus | null {
  // A failed attempt sits off the happy path: its forward move is a
  // re-dispatch, back to OUT_FOR_DELIVERY with the same stock reservation.
  if (status === 'DELIVERY_FAILED') return 'OUT_FOR_DELIVERY';
  const i = FLOW.indexOf(status);
  if (i < 0 || i >= FLOW.length - 1) return null;
  return FLOW[i + 1];
}

/**
 * The step whose next move normally belongs to the RIDER, not to staff.
 *
 * <p>A bagged order waits on the counter until the rider taps "Picked up" in
 * their own app, which is what makes the customer's tracking map and delivery
 * code go live. Staff keep the button — a rider with a flat phone still has to
 * be able to set off — but it reads as recording the hand-over rather than as
 * the next thing to click.
 */
export function awaitsRiderPickup(status: OrderStatus): boolean {
  return status === 'READY_FOR_DELIVERY';
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
  { value: 'READY_FOR_DELIVERY', label: 'Ready for delivery' },
  { value: 'OUT_FOR_DELIVERY', label: 'Out for delivery' },
  { value: 'DELIVERY_FAILED', label: 'Delivery failed' },
  { value: 'DELIVERED', label: 'Delivered' },
  { value: 'CANCELLED', label: 'Cancelled' },
];

/**
 * Why an order is asking for a person right now, or null when it is not.
 *
 * <p>These are the moments the queue is waiting on staff, not on the customer
 * or the rider: a new order nobody has accepted, a delivery that came back and
 * needs a decision, and a packed bag with no rider to take it. The in-progress
 * steps (confirmed, packing, out for delivery) are deliberately absent — they
 * are being worked, and a glow on every card would stop meaning anything.
 */
export type ActionReason = 'new' | 'failed' | 'rider';

export function actionNeeded(order: Pick<Order, 'status' | 'assignedAgentId'>): ActionReason | null {
  switch (order.status) {
    case 'PLACED':
      return 'new';
    case 'DELIVERY_FAILED':
      return 'failed';
    case 'READY_FOR_DELIVERY':
      return order.assignedAgentId == null ? 'rider' : null;
    default:
      return null;
  }
}
