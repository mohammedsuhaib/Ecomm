package com.townbasket.catalog;

import java.util.Locale;
import java.util.Optional;

/**
 * Optional ordering for product list/search endpoints (B2).
 *
 * <ul>
 *   <li>{@link #NAME} — A–Z by product name, case-insensitive.</li>
 *   <li>{@link #NAME_DESC} — Z–A by product name, case-insensitive.</li>
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
    NAME_DESC,
    PRICE_ASC,
    PRICE_DESC,
    DISCOUNT;

    /** Symmetric spelling of {@link #NAME}, to pair with {@code name_desc}. */
    private static final String NAME_ASC_ALIAS = "NAME_ASC";

    /**
     * Parse the public {@code sort} query value (e.g. {@code name}, {@code price_asc}).
     * Case-insensitive; absent/blank/unknown values yield an empty optional (default order).
     *
     * <p>{@code name_asc} is accepted as a synonym for {@code name}: the pair reads
     * symmetrically alongside {@code name_desc}, while the original {@code name}
     * keeps working so existing links and bookmarks don't silently lose their order.
     */
    public static Optional<ProductSort> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String key = value.trim().toUpperCase(Locale.ROOT);
        if (NAME_ASC_ALIAS.equals(key)) {
            return Optional.of(NAME);
        }
        try {
            return Optional.of(valueOf(key));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
