package com.townbasket.shared.events;

/**
 * Published by {@code orders} when a new order is persisted (after stock has
 * been reserved). Carried in the OPEN {@code shared} module so any module may
 * react to it. Cross-module references are by id (Long) only.
 */
public record OrderPlaced(
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
