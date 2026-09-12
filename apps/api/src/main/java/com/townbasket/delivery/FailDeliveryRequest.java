package com.townbasket.delivery;

/**
 * Body for {@code POST /delivery/orders/{id}/fail}. {@code reason} is required —
 * it is what staff read when deciding whether to re-attempt or cancel, and what
 * the customer is told.
 */
public record FailDeliveryRequest(String reason) {
}
