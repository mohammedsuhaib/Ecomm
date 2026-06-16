package com.townbasket.payments;

/**
 * Outcome of charging an order, returned by {@link PaymentService#charge} to the
 * orders checkout. {@code reference} is the provider transaction reference (null
 * for COD).
 */
public record PaymentResult(
        Long paymentId,
        PaymentMethod method,
        PaymentStatus status,
        String reference) {

    /** True when the order may be confirmed immediately (UPI PAID, or COD). */
    public boolean confirmsOrder() {
        return status == PaymentStatus.PAID || status == PaymentStatus.COD_PENDING;
    }
}
