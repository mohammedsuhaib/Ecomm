package com.townbasket.orders;

import java.math.BigDecimal;

/**
 * A single order line in an order response. Snapshotted at the time of sale.
 *
 * <p><strong>No cost price.</strong> The COGS snapshot persisted on the order
 * item is internal-only and is intentionally absent from this DTO.
 */
public record OrderItemDto(
        String productName,
        String label,
        BigDecimal unitPrice,
        int qty,
        BigDecimal lineTotal) {
}
