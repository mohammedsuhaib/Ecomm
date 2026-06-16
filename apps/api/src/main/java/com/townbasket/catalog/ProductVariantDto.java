package com.townbasket.catalog;

import java.math.BigDecimal;

/**
 * Public product-variant representation. Prices are JSON decimals in rupees
 * (e.g. {@code 45.00}).
 *
 * <p><strong>cost_price is deliberately absent</strong> — it is internal-only
 * and must never be exposed in any API response.
 */
public record ProductVariantDto(
        Long id,
        String label,
        BigDecimal sellingPrice,
        BigDecimal mrp,
        boolean available) {
}
