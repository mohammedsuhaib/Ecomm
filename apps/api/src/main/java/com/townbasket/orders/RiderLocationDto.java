package com.townbasket.orders;

import java.time.Instant;

/**
 * The delivery rider's current position, as shown to the customer on the
 * tracking page while their order is out for delivery.
 *
 * <p>Present on {@link OrderDto} <strong>only</strong> on the customer-facing
 * tracking read, only while the order is {@code OUT_FOR_DELIVERY}, only when a
 * rider is assigned, and only when the fix is recent — the same shape of gate
 * as {@code deliveryOtp}, for a related reason: a rider's whereabouts belong to
 * the customer they are driving to at that moment, and to nobody else. It is
 * never included on the admin or delivery surfaces, and never for an order in
 * any other status.
 *
 * @param recordedAt when the server accepted the fix (store clock, not the
 *                   phone's), so the UI can say how old it is
 */
public record RiderLocationDto(double lat, double lng, Instant recordedAt) {
}
