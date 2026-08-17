package com.townbasket.catalog;

import java.math.BigDecimal;

/**
 * Cross-module tax lookup for a variant (via its owning product), consumed by
 * {@code orders} to snapshot the GST rate + HSN code on each order line at
 * checkout. Like {@link VariantView}, this is NOT an API-response DTO.
 *
 * @param hsnCode        HSN classification for the invoice; may be {@code null}
 *                       until staff fill it in
 * @param gstRatePercent GST slab as a percentage (0, 5, 18, 40)
 */
public record VariantTaxView(String hsnCode, BigDecimal gstRatePercent) {
}
