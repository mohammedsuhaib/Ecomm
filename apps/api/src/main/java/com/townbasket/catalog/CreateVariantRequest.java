package com.townbasket.catalog;

import java.math.BigDecimal;

/**
 * Admin request to create a product variant. Plain record — validation lives in
 * the service ({@code label} non-blank; {@code sellingPrice}/{@code costPrice}
 * non-null and {@code >= 0}).
 *
 * @param label        required, non-blank (e.g. "1 kg")
 * @param sellingPrice required, {@code >= 0}
 * @param costPrice    required, {@code >= 0} (internal COGS)
 * @param mrp          optional
 * @param available    optional — defaults to {@code true}
 * @param sortOrder    optional — defaults to 0
 */
public record CreateVariantRequest(
        String label,
        BigDecimal sellingPrice,
        BigDecimal costPrice,
        BigDecimal mrp,
        Boolean available,
        Integer sortOrder) {
}
