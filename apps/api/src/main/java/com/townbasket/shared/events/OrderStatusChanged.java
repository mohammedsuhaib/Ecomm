package com.townbasket.shared.events;

/**
 * Published by {@code orders} on every staff-driven state-machine transition.
 * {@code fromStatus}/{@code toStatus} are the order-status names (e.g.
 * {@code "CONFIRMED"} -> {@code "PACKING"}). Consumed by {@code notifications}
 * to push live tracking + admin-queue updates.
 *
 * <p>{@code userId} and {@code trackingToken} let notifications reach the
 * customer off-page: the user id selects their Web Push subscriptions, and the
 * token deep-links the notification to their tracking page. {@code userId} is
 * {@code null} for orders placed before login was required.
 */
public record OrderStatusChanged(
        Long orderId,
        Long storeId,
        String fromStatus,
        String toStatus,
        Long userId,
        String trackingToken) {
}
