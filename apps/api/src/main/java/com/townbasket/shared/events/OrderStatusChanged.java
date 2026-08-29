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
 *
 * <p>{@code assignedAgentId} is the rider currently carrying the order, so the
 * transitions that change what they do (ready to collect, cancelled) can reach
 * them; {@code addressLine} makes that notification useful on its own. Both are
 * only ever delivered to that one rider. {@code assignedAgentId} is
 * {@code null} when the order has not been assigned.
 */
public record OrderStatusChanged(
        Long orderId,
        Long storeId,
        String fromStatus,
        String toStatus,
        Long userId,
        String trackingToken,
        Long assignedAgentId,
        String addressLine) {
}
