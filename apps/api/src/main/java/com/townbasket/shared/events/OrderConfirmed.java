package com.townbasket.shared.events;

/**
 * Published by {@code orders} once an order reaches CONFIRMED (COD at placement,
 * or UPI after payment succeeds). Consumed by {@code notifications}.
 */
public record OrderConfirmed(
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
