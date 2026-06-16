package com.townbasket.orders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Public order representation (confirmation + tracking + admin queue).
 *
 * <p>{@code deliveryOtp} IS returned (shown to the customer; required to mark the
 * order delivered). Per-line {@code cost_price} (COGS) is NEVER exposed — see
 * {@link OrderItemDto}.
 */
public record OrderDto(
        Long id,
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
