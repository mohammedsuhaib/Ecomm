package com.townbasket.catalog;

import java.math.BigDecimal;

/**
 * Admin-facing product-variant representation. Unlike the public
 * {@link ProductVariantDto}, this DTO <strong>includes {@code costPrice}</strong>
 * (COGS) because the admin/staff catalogue UI needs to manage it. It is returned
 * only from the staff-secured {@code /api/v1/admin/catalog/**} surface.
 *
 * <p>Prices are JSON decimals in rupees (e.g. {@code 45.00}).
 */
public record AdminVariantDto(
        Long id,
        String label,
        BigDecimal sellingPrice,
        BigDecimal costPrice,
        BigDecimal mrp,
        boolean available,
        int sortOrder) {
}
