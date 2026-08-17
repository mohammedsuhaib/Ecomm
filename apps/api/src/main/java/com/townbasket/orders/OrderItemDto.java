package com.townbasket.orders;

import java.math.BigDecimal;

/**
 * A single order line in an order response. Snapshotted at the time of sale.
 *
 * <p>The GST fields decompose {@code lineTotal} (prices are tax-inclusive):
 * {@code taxableValue + cgst + sgst == lineTotal}. They power the invoice's
 * statutory breakdown; {@code hsnCode} may be {@code null} until staff fill it
 * in on the product.
 *
 * <p><strong>No cost price.</strong> The COGS snapshot persisted on the order
 * item is internal-only and is intentionally absent from this DTO.
 */
public record OrderItemDto(
        String productName,
        String label,
        BigDecimal unitPrice,
        int qty,
        BigDecimal lineTotal,
        String hsnCode,
        BigDecimal gstRatePercent,
        BigDecimal taxableValue,
        BigDecimal cgst,
        BigDecimal sgst) {
}
