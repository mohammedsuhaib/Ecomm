package com.townbasket.cart;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Public cart representation. {@code subtotal} is the sum of line totals;
 * {@code itemCount} is the total quantity across lines. Prices and availability
 * are resolved from the catalog public API at read time. {@code checkedOut} is
 * true once the cart has been turned into an order (it can no longer be ordered
 * again).
 */
public record CartDto(
        UUID cartId,
        List<CartItemDto> items,
        BigDecimal subtotal,
        int itemCount,
        boolean checkedOut) {
}
