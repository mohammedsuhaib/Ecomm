package com.townbasket.payments;

/**
 * Outcome of charging an order, returned by {@link PaymentService#charge} to the
 * orders checkout. {@code reference} is the provider transaction reference (null
 * for COD). The order's status is not decided here: checkout records
 * {@link #status()} on the order and leaves it PLACED for staff to confirm
 * from the admin queue.
 */
public record PaymentResult(
        PaymentMethod method,
        PaymentStatus status,
        String reference) {
}
