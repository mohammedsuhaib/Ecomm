package com.townbasket.orders.internal;

import java.util.Set;

/**
 * The order state machine (ARCHITECTURE §3.5):
 *
 * <pre>
 * PLACED -> CONFIRMED -> PACKING -> OUT_FOR_DELIVERY -> DELIVERED
 *    |__________|__________|__________|__> CANCELLED
 *                                     |
 *                                     v
 *                              DELIVERY_FAILED --> OUT_FOR_DELIVERY (re-attempt)
 *                                     |__> CANCELLED (goods back on the shelf)
 * </pre>
 *
 * Module-internal. The transition rules live here so the admin endpoint can
 * enforce them. {@code DELIVERED} additionally requires a matching delivery OTP
 * and {@code DELIVERY_FAILED} a reason (both enforced by the service).
 *
 * <p>{@code DELIVERY_FAILED} exists because a rider at a door with no one
 * answering had only CANCELLED before — which released the reserved stock
 * while the goods were still in a bag on a bike. A failed attempt keeps the
 * reservation (the goods are packed and out), records why, and leaves the
 * order for staff to re-dispatch or cancel once the bag is actually back.
 */
enum OrderStatus {
    PLACED,
    CONFIRMED,
    PACKING,
    OUT_FOR_DELIVERY,
    DELIVERY_FAILED,
    DELIVERED,
    CANCELLED;

    Set<OrderStatus> allowedNext() {
        return switch (this) {
            case PLACED -> Set.of(CONFIRMED, CANCELLED);
            case CONFIRMED -> Set.of(PACKING, CANCELLED);
            case PACKING -> Set.of(OUT_FOR_DELIVERY, CANCELLED);
            case OUT_FOR_DELIVERY -> Set.of(DELIVERED, DELIVERY_FAILED, CANCELLED);
            case DELIVERY_FAILED -> Set.of(OUT_FOR_DELIVERY, CANCELLED);
            case DELIVERED, CANCELLED -> Set.of();
        };
    }

    boolean canTransitionTo(OrderStatus target) {
        return allowedNext().contains(target);
    }
}
