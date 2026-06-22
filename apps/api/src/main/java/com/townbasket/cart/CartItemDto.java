package com.townbasket.cart;

import java.math.BigDecimal;

/**
 * A single cart line, resolved against the catalog at read time. Carries enough
 * for the storefront to render the line and for {@code orders} to build the
 * order. No cost price.
 *
 * <p>{@code available} reflects the catalog availability flag (admin toggle).
 * {@code availableStock} is the live, sellable quantity ({@code on_hand -
 * reserved}) from inventory, so the storefront can warn about a shortage
 * <em>before</em> checkout instead of only at the final reservation.
 */
public record CartItemDto(
        Long itemId,
        Long variantId,
        Long productId,
        String productName,
        String label,
        BigDecimal unitPrice,
        int qty,
        BigDecimal lineTotal,
        boolean available,
        int availableStock) {
}
