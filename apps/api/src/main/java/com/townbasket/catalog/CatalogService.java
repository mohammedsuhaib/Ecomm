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

    // ----------------------------------------------------------------------
    // Admin write surface (staff/admin only — secured by SecurityConfig's
    // /api/v1/admin/** matcher). Returns admin DTOs that DO include cost price.
    // Throws ResourceNotFoundException for missing ids and BusinessRuleException
    // for rule violations; both are mapped by GlobalExceptionHandler.
    // ----------------------------------------------------------------------

    /** Admin category list (ordered by sort order, then name). */
    List<CategoryDto> adminListCategories();

    /** Create a category (auto-slug + default sort order when omitted). */
    CategoryDto createCategory(CreateCategoryRequest request);

    /** Update a category. Slug is immutable. */
    CategoryDto updateCategory(Long id, UpdateCategoryRequest request);

    /** Delete a category. Throws {@link com.townbasket.shared.BusinessRuleException} if it still has products. */
    void deleteCategory(Long id);

    /**
     * Admin product list including UNAVAILABLE products. When {@code q} is present,
     * search by name; otherwise list all, optionally scoped to {@code categoryId}.
     */
    PagedResponse<AdminProductDto> adminListProducts(Long categoryId, String q, Pageable pageable);

    /** Admin single-product lookup by id (includes cost price). */
    AdminProductDto adminGetProduct(Long id);

    /** Create a product (optionally with variants). */
    AdminProductDto createProduct(CreateProductRequest request);

    /** Update a product. Slug is immutable; variants are managed separately. */
    AdminProductDto updateProduct(Long id, UpdateProductRequest request);

    /** Toggle a product's availability. */
    AdminProductDto setProductAvailability(Long id, boolean available);

    /** Hard-delete a product (cascades its variants). */
    void deleteProduct(Long id);

    /** Add a variant to a product. */
    AdminVariantDto addVariant(Long productId, CreateVariantRequest request);

    /** Update a variant of a product. */
    AdminVariantDto updateVariant(Long productId, Long variantId, UpdateVariantRequest request);

    /** Toggle a variant's availability. */
    AdminVariantDto setVariantAvailability(Long productId, Long variantId, boolean available);

    /** Delete a variant of a product. */
    void deleteVariant(Long productId, Long variantId);
}
