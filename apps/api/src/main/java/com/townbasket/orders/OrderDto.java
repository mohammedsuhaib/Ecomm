package com.townbasket.orders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Public order representation (confirmation + tracking + admin queue).
 *
 * <p>{@code trackingToken} is the unguessable capability the customer uses to
 * fetch/track this order; it replaces the sequential numeric id on the
 * customer-facing endpoints so orders can't be harvested by id enumeration.
 *
 * <p>{@code deliveryOtp} is the proof-of-delivery / COD-fraud code. It is
 * exposed to the <strong>customer only while the order is OUT_FOR_DELIVERY</strong>
 * (it is {@code null} at every other status, and never returned on the admin
 * surface) — staff must collect it from the customer at handover, so seeing it
 * earlier would defeat the control. Per-line {@code cost_price} (COGS) is NEVER
 * exposed — see {@link OrderItemDto}.
 */
public record OrderDto(
        Long id,
        String trackingToken,
        String status,
        String paymentMethod,
        String paymentStatus,
        String customerName,
        String phone,
        AddressDto address,
        List<OrderItemDto> items,
        BigDecimal subtotal,
        BigDecimal total,
        String deliveryOtp,
        Instant placedAt,
        List<OrderTimelineEntryDto> timeline) {
}
