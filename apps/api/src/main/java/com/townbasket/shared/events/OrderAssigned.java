package com.townbasket.shared.events;

/**
 * Published by {@code orders} when an order's delivery agent changes. Consumed
 * by {@code notifications} to tell the rider they have a new job — the one
 * moment a rider genuinely needs to be interrupted, since they are out on the
 * road and not watching a screen.
 *
 * <p>Both sides of a hand-over are carried so the previous rider can be told the
 * job is no longer theirs and does not drive to it. {@code agentId} is
 * {@code null} when the order is returned to the unassigned pool;
 * {@code previousAgentId} is {@code null} for a first assignment.
 *
 * @param status      the order's status at the moment of assignment, so
 *                    notifications can hold a heads-up until the job is
 *                    actually collectable
 * @param addressLine where the delivery goes, so the notification is useful on
 *                    its own — it reaches only the rider the order is assigned to
 */
public record OrderAssigned(
        Long orderId,
        /**
         * The short, customer-facing order code (see {@code orders.public_code}).
         * Notification text must use THIS, never {@code orderId}: the id is
         * sequential, so putting it in a customer's notification publishes the
         * store's order volume. May be null on an event that was serialised into
         * the outbox before this field existed — consumers fall back.
         */
        String publicCode,
        Long storeId,
        Long agentId,
        Long previousAgentId,
        String status,
        String addressLine) {
}
