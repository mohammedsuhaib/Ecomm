package com.townbasket.catalog;

import java.math.BigDecimal;

/**
 * Admin request to update a product variant. Plain record — validation lives in
 * the service ({@code label} non-blank; {@code sellingPrice}/{@code costPrice}
 * non-null and {@code >= 0}).
 *
 * @param label        required, non-blank
 * @param sellingPrice required, {@code >= 0}
 * @param costPrice    required, {@code >= 0}
 * @param mrp          optional (may be set to {@code null} to clear)
 * @param available    optional — when {@code null}, the existing value is kept
 * @param sortOrder    optional — when {@code null}, the existing value is kept
 */
public record UpdateVariantRequest(
        String label,
        BigDecimal sellingPrice,
        BigDecimal costPrice,
        BigDecimal mrp,
        Boolean available,
        Integer sortOrder) {
}
