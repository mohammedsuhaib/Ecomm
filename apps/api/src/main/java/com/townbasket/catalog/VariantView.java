package com.townbasket.catalog;

import java.math.BigDecimal;

/**
 * Cross-module variant lookup result returned by {@link CatalogService} so that
 * {@code cart} and {@code orders} can resolve a variant by id without reaching
 * across schemas. Identifies the variant, its owning product, the customer-
 * facing label/price, and current availability.
 *
 * <p><strong>This is NOT an API-response DTO.</strong> It is consumed only by
 * other modules' services (by id). It deliberately does NOT carry cost price —
 * COGS is fetched separately via {@link CatalogService#costPrice(Long)} for the
 * orders snapshot and is never serialized to any client.
 *
 * <p>Both names travel together, and {@code productNameKn} may be {@code null}:
 * a cart line is the same product the customer was just browsing, so it has to
 * be nameable in the same language. Resolving the name per-request in the
 * storefront instead is not an option for {@code orders}, which snapshots the
 * name at the moment of sale.
 */
public record VariantView(
        Long variantId,
        Long productId,
        String productName,
        String productNameKn,
        String label,
        BigDecimal unitPrice,
        boolean available) {
}
