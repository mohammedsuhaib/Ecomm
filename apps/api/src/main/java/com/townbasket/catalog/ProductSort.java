package com.townbasket.catalog;

import java.util.Optional;

/**
 * Optional ordering for product list/search endpoints (B2).
 *
 * <ul>
 *   <li>{@link #NAME} — A–Z by product name, case-insensitive.</li>
 *   <li>{@link #PRICE_ASC} — ascending by the product's LOWEST available variant selling price.</li>
 *   <li>{@link #PRICE_DESC} — descending by that same lowest available variant selling price.</li>
 *   <li>{@link #DISCOUNT} — descending by the product's MAX variant discount
 *       ({@code mrp - sellingPrice}; a null mrp counts as zero discount).</li>
 * </ul>
 *
 * <p>An absent or unrecognised {@code sort} query value maps to {@link Optional#empty()}, which
 * preserves the endpoint's default ordering (insertion / relevance) unchanged.
 */
public enum ProductSort {
    NAME,
    PRICE_ASC,
    PRICE_DESC,
    DISCOUNT;

    /**
     * Parse the public {@code sort} query value (e.g. {@code name}, {@code price_asc}).
     * Case-insensitive; absent/blank/unknown values yield an empty optional (default order).
     */
    public static Optional<ProductSort> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        return switch (value.trim().toLowerCase()) {
            case "name" -> Optional.of(NAME);
            case "price_asc" -> Optional.of(PRICE_ASC);
            case "price_desc" -> Optional.of(PRICE_DESC);
            case "discount" -> Optional.of(DISCOUNT);
            default -> Optional.empty();
        };
    }
}
