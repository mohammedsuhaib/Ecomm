package com.townbasket.catalog;

import java.math.BigDecimal;

/**
 * Public product-variant representation. Prices are JSON decimals in rupees
 * (e.g. {@code 45.00}).
 *
 * <p><strong>cost_price is deliberately absent</strong> — it is internal-only
 * and must never be exposed in any API response.
 *
 * <p>{@code available} is the store's manual on/off toggle; {@code availableStock}
 * is the live sellable count ({@code on_hand - reserved}) from the inventory
 * module, so the storefront can show "out of stock" before checkout, not just in
 * the cart.
 */
public record ProductVariantDto(
        Long id,
        String label,
        BigDecimal sellingPrice,
        BigDecimal mrp,
        boolean available,
        int availableStock) {
}
