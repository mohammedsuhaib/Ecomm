package com.townbasket.catalog;

import java.math.BigDecimal;

/**
 * Admin request to update a product. The slug is immutable (not editable), so it
 * is intentionally absent. Variants are managed through the dedicated variant
 * endpoints. Plain record — validation lives in the service.
 *
 * @param name        required, non-blank
 * @param nameKn      optional Kannada name (see {@link CreateProductRequest})
 * @param categoryId  required, must reference an existing category
 * @param description optional
 * @param vegMarker   optional — when {@code null}, the existing value is kept
 * @param imageUrl    optional — when {@code null}, the existing value is kept
 * @param available   optional — when {@code null}, the existing value is kept
 * @param featured    optional — when {@code null}, the existing value is kept
 * @param hsnCode     optional — when {@code null}, the existing value is kept;
 *                    blank clears it
 * @param gstRatePercent optional — when {@code null}, the existing value is
 *                    kept; must be a valid GST slab (0/5/18/40)
 */
public record UpdateProductRequest(
        String name,
        String nameKn,
        Long categoryId,
        String description,
        Boolean vegMarker,
        String imageUrl,
        Boolean available,
        Boolean featured,
        String hsnCode,
        BigDecimal gstRatePercent) {
}
