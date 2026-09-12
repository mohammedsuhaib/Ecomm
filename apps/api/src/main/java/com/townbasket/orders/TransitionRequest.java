package com.townbasket.orders;

/**
 * Admin order-transition request. {@code to} is the target status name
 * (CONFIRMED, PACKING, OUT_FOR_DELIVERY, DELIVERY_FAILED, DELIVERED, CANCELLED).
 * {@code deliveryOtp} is required when transitioning to DELIVERED; {@code reason}
 * is recorded on the timeline — REQUIRED for DELIVERY_FAILED (staff need to know
 * why the bag came back) and required-by-convention for CANCELLED.
 */
public record TransitionRequest(String to, String deliveryOtp, String reason) {
}
