package com.townbasket.orders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Public order representation (confirmation + tracking + admin queue).
 *
 * <p>Three identifiers, three jobs. {@code id} is the internal key (admin and
 * delivery routes, foreign keys) and should not be shown to customers — being
 * sequential, it publishes the store's order volume. {@code trackingToken} is
 * the unguessable URL handle the customer fetches this order by, so orders
 * can't be harvested by id enumeration. {@code publicCode} is the short,
 * speakable order number customers quote and staff search by.
 *
 * <p>{@code invoiceNumber} / {@code invoicedAt} are null until a GST invoice is
 * actually issued for the order, and immutable afterwards — the number comes
 * from a per-financial-year consecutive series (CGST Rule 46(b)), not from the
 * order id.
 *
 * <p>{@code deliveryOtp} is the proof-of-delivery / COD-fraud code. It is
 * exposed to the <strong>customer only while the order is OUT_FOR_DELIVERY</strong>
 * (it is {@code null} at every other status, and never returned on the admin
 * surface) — staff must collect it from the customer at handover, so seeing it
 * earlier would defeat the control. Per-line {@code cost_price} (COGS) is NEVER
 * exposed — see {@link OrderItemDto}.
 *
 * <p>{@code riderLocation} is gated the same way and then some: it is set only
 * on the customer's tracking read, only while OUT_FOR_DELIVERY, only with a
 * rider assigned, and only while their last fix is recent (see
 * {@link RiderLocationDto}). Null everywhere else, including every list and
 * the whole admin and delivery surface.
 */
public record OrderDto(
        Long id,
        String trackingToken,
        String publicCode,
        String status,
        String paymentMethod,
        String paymentStatus,
        String customerName,
        String phone,
        AddressDto address,
        List<OrderItemDto> items,
        BigDecimal subtotal,
        BigDecimal total,
        BigDecimal totalTax,
        String deliveryOtp,
        Instant placedAt,
        List<OrderTimelineEntryDto> timeline,
        Long assignedAgentId,
        String invoiceNumber,
        Instant invoicedAt,
        RiderLocationDto riderLocation) {
}
