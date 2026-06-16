package com.townbasket.shared.events;

/**
 * Published by {@code orders} on every staff-driven state-machine transition.
 * {@code fromStatus}/{@code toStatus} are the order-status names (e.g.
 * {@code "CONFIRMED"} -> {@code "PACKING"}). Consumed by {@code notifications}
 * to push live tracking + admin-queue updates.
 */
public record OrderStatusChanged(
        Long orderId,
        Long storeId,
        String fromStatus,
        String toStatus) {
}
