package com.townbasket.cart;

import java.math.BigDecimal;

/**
 * A single cart line, resolved against the catalog at read time. Carries enough
 * for the storefront to render the line and for {@code orders} to build the
 * order. No cost price.
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
        boolean available) {
}
