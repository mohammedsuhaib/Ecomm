package com.townbasket.shared.events;

/**
 * Published by {@code orders} when an order reaches DELIVERED (after the
 * delivery OTP is verified). Consumed by {@code inventory} to commit the
 * reservation (on_hand -= qty), and by {@code notifications}.
 */
public record OrderDelivered(
        Long orderId,
        /**
         * The short, customer-facing order code (see {@code orders.public_code}).
         * Notification text must use THIS, never {@code orderId}: the id is
         * sequential, so putting it in a customer's notification publishes the
         * store's order volume. May be null on an event that was serialised into
         * the outbox before this field existed — consumers fall back.
         */
        String publicCode,
        Long storeId) {
}
