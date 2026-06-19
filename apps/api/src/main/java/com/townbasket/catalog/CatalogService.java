package com.townbasket.catalog;

import com.townbasket.shared.PagedResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;

/**
 * Published API of the catalog module. Read-only browsing plus cross-module
 * variant lookups used by {@code cart} and {@code orders} (by id only).
 *
 * <p>Returns DTOs / views only — entities and repositories stay module-internal.
 */
public interface CatalogService {

    List<CategoryDto> listCategories();

    /**
     * List products, optionally filtered by category and/or only featured items,
     * with an optional {@link ProductSort} (sorts the full filtered set, then pages).
     *
     * @param categoryId optional category filter ({@code null} = all categories)
     * @param featured   when {@code true}, return only featured products
     * @param sort       optional ordering; {@code null} = default (insertion) order
     */
    PagedResponse<ProductDto> listProducts(Long categoryId, boolean featured, ProductSort sort, Pageable pageable);

    /** Look up a product by numeric id or by slug. */
    Optional<ProductDto> findProduct(String idOrSlug);

    /**
     * Full-text + trigram search, with an optional {@link ProductSort} (sorts the
     * full filtered set, then pages). {@code null} sort preserves relevance order.
     */
    PagedResponse<ProductDto> search(String query, ProductSort sort, Pageable pageable);

    /**
     * Resolve a single variant (product name, label, selling price, availability)
     * by id, for {@code cart} / {@code orders}. Empty if no such variant.
     * Never includes cost price.
     */
    Optional<VariantView> findVariant(Long variantId);

    /**
     * Cost price (COGS) for a variant, for the orders module's per-line
     * snapshot only. INTERNAL — never returned to any client. Empty if no such
     * variant.
     */
    Optional<BigDecimal> costPrice(Long variantId);
}
