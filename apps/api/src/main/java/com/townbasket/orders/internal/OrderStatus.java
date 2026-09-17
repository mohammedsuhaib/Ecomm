package com.townbasket.orders.internal;

import java.util.Set;

/**
 * The order state machine (ARCHITECTURE §3.5):
 *
 * <pre>
 * PLACED -> CONFIRMED -> PACKING -> READY_FOR_DELIVERY -> OUT_FOR_DELIVERY -> DELIVERED
 *    |__________|__________|______________|_________________|__> CANCELLED
 *                                                           |
 *                                                           v
 *                                                    DELIVERY_FAILED --> OUT_FOR_DELIVERY (re-attempt)
 *                                                           |__> CANCELLED (goods back on the shelf)
 * </pre>
 *
 * Module-internal. The transition rules live here so the admin endpoint can
 * enforce them. {@code DELIVERED} additionally requires a matching delivery OTP
 * and {@code DELIVERY_FAILED} a reason (both enforced by the service).
 *
 * <p>{@code READY_FOR_DELIVERY} splits what used to be a single staff step.
 * "Packed" and "on a bike" were the same transition, so the queue said an order
 * had left the shop from the moment the packer finished it — while the bag sat
 * on the counter, the customer watched a rider who was not moving, and nobody
 * could tell an order waiting for a rider from one already on the road. The
 * pack-up is now the store's own step and the hand-over is the rider's: the
 * order waits in READY_FOR_DELIVERY until the rider says they have it (see
 * {@code OrderService#pickUp}), which is also the moment the customer's
 * tracking map and delivery OTP become meaningful.
 *
 * <p>{@code DELIVERY_FAILED} exists because a rider at a door with no one
 * answering had only CANCELLED before — which released the reserved stock
 * while the goods were still in a bag on a bike. A failed attempt keeps the
 * reservation (the goods are packed and out), records why, and leaves the
 * order for staff to re-dispatch or cancel once the bag is actually back. Its
 * forward move stays a direct OUT_FOR_DELIVERY on purpose: the bag is normally
 * still with the rider, so there is nothing to collect from the counter again.
 */
enum OrderStatus {
    PLACED,
    CONFIRMED,
    PACKING,
    READY_FOR_DELIVERY,
    OUT_FOR_DELIVERY,
    DELIVERY_FAILED,
    DELIVERED,
    CANCELLED;

    Set<OrderStatus> allowedNext() {
        return switch (this) {
            case PLACED -> Set.of(CONFIRMED, CANCELLED);
            case CONFIRMED -> Set.of(PACKING, CANCELLED);
            case PACKING -> Set.of(READY_FOR_DELIVERY, CANCELLED);
            case READY_FOR_DELIVERY -> Set.of(OUT_FOR_DELIVERY, CANCELLED);
            case OUT_FOR_DELIVERY -> Set.of(DELIVERED, DELIVERY_FAILED, CANCELLED);
            case DELIVERY_FAILED -> Set.of(OUT_FOR_DELIVERY, CANCELLED);
            case DELIVERED, CANCELLED -> Set.of();
        };
    }

    boolean canTransitionTo(OrderStatus target) {
        return allowedNext().contains(target);
    }
}
