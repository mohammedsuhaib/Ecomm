package com.townbasket.orders.internal;

import java.util.Set;

/**
 * The order state machine (ARCHITECTURE §3.5):
 *
 * <pre>
 * PLACED -> CONFIRMED -> PACKING -> OUT_FOR_DELIVERY -> DELIVERED
 *    |__________|__________|__> CANCELLED (pre-delivery)
 * </pre>
 *
 * Module-internal. The transition rules live here so the admin endpoint can
 * enforce them. {@code DELIVERED} additionally requires a matching delivery OTP
 * (enforced by the service, not modeled here).
 */
enum OrderStatus {
    PLACED,
    CONFIRMED,
    PACKING,
    OUT_FOR_DELIVERY,
    DELIVERED,
    CANCELLED;

    Set<OrderStatus> allowedNext() {
        return switch (this) {
            case PLACED -> Set.of(CONFIRMED, CANCELLED);
            case CONFIRMED -> Set.of(PACKING, CANCELLED);
            case PACKING -> Set.of(OUT_FOR_DELIVERY, CANCELLED);
            case OUT_FOR_DELIVERY -> Set.of(DELIVERED, CANCELLED);
            case DELIVERED, CANCELLED -> Set.of();
        };
    }

    boolean canTransitionTo(OrderStatus target) {
        return allowedNext().contains(target);
    }
}
