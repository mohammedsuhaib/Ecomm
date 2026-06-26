package com.townbasket.catalog;

import com.townbasket.shared.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin catalogue REST API under {@code /api/v1/admin/catalog}: write management
 * of categories, products and variants.
 *
 * <p>Secured to {@code STORE_STAFF}/{@code ADMIN} by {@code SecurityConfig}'s
 * {@code /api/v1/admin/**} matcher (no method-level {@code @PreAuthorize} needed).
 * Delegates to {@link CatalogService}; entities never cross the module boundary.
 *
 * <p>Unlike the public catalog DTOs, the {@link AdminProductDto} /
 * {@link AdminVariantDto} returned here DO include {@code costPrice} — the staff
 * UI manages COGS.
 */
@RestController
@RequestMapping("/api/v1/admin/catalog")
@Tag(name = "Admin Catalog", description = "Staff management of categories, products and variants.")
class AdminCatalogController {

    private static final int MAX_PAGE_SIZE = 100;

    private final CatalogService catalogService;

    AdminCatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    // ---- categories -----------------------------------------------------

    @GetMapping("/categories")
    @Operation(summary = "List all categories ordered by sort order.")
    List<CategoryDto> listCategories() {
        return catalogService.adminListCategories();
    }

    @PostMapping("/categories")
    @Operation(summary = "Create a category (slug auto-generated when omitted).")
    CategoryDto createCategory(@RequestBody CreateCategoryRequest request) {
        return catalogService.createCategory(request);
    }

    @PutMapping("/categories/{id}")
    @Operation(summary = "Update a category (slug is immutable).")
    CategoryDto updateCategory(@PathVariable Long id, @RequestBody UpdateCategoryRequest request) {
        return catalogService.updateCategory(id, request);
    }

    @DeleteMapping("/categories/{id}")
    @Operation(summary = "Delete a category (422 if it still has products).")
    ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        catalogService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }

    // ---- products -------------------------------------------------------

    @GetMapping("/products")
    @Operation(summary = "List products INCLUDING unavailable ones; filter by category and/or name (paginated).")
    PagedResponse<AdminProductDto> listProducts(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return catalogService.adminListProducts(categoryId, q, pageable(page, size));
    }

    @GetMapping("/products/{id}")
    @Operation(summary = "Fetch a single product by id (includes cost price).")
    AdminProductDto getProduct(@PathVariable Long id) {
        return catalogService.adminGetProduct(id);
    }

    @PostMapping("/products")
    @Operation(summary = "Create a product (optionally with variants; slug auto-generated when omitted).")
    AdminProductDto createProduct(@RequestBody CreateProductRequest request) {
        return catalogService.createProduct(request);
    }

    @PutMapping("/products/{id}")
    @Operation(summary = "Update a product (slug is immutable; variants managed separately).")
    AdminProductDto updateProduct(@PathVariable Long id, @RequestBody UpdateProductRequest request) {
        return catalogService.updateProduct(id, request);
    }

    @PostMapping("/products/{id}/availability")
    @Operation(summary = "Toggle a product's availability.")
    AdminProductDto setProductAvailability(@PathVariable Long id, @RequestBody SetAvailabilityRequest request) {
        return catalogService.setProductAvailability(id, request.available());
    }

    @DeleteMapping("/products/{id}")
    @Operation(summary = "Hard-delete a product (cascades its variants).")
    ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        catalogService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    // ---- variants -------------------------------------------------------

    @PostMapping("/products/{id}/variants")
    @Operation(summary = "Add a variant to a product.")
    ResponseEntity<AdminVariantDto> addVariant(@PathVariable Long id, @RequestBody CreateVariantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(catalogService.addVariant(id, request));
    }

    @PutMapping("/products/{id}/variants/{variantId}")
    @Operation(summary = "Update a variant.")
    AdminVariantDto updateVariant(@PathVariable Long id, @PathVariable Long variantId,
                                  @RequestBody UpdateVariantRequest request) {
        return catalogService.updateVariant(id, variantId, request);
    }

    @PostMapping("/products/{id}/variants/{variantId}/availability")
    @Operation(summary = "Toggle a variant's availability.")
    AdminVariantDto setVariantAvailability(@PathVariable Long id, @PathVariable Long variantId,
                                           @RequestBody SetAvailabilityRequest request) {
        return catalogService.setVariantAvailability(id, variantId, request.available());
    }

    @DeleteMapping("/products/{id}/variants/{variantId}")
    @Operation(summary = "Delete a variant.")
    ResponseEntity<Void> deleteVariant(@PathVariable Long id, @PathVariable Long variantId) {
        catalogService.deleteVariant(id, variantId);
        return ResponseEntity.noContent().build();
    }

    private static Pageable pageable(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize);
    }
}
