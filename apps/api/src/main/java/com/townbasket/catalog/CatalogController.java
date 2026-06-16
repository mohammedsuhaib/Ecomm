package com.townbasket.catalog;

import com.townbasket.shared.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only catalog REST API under {@code /api/v1}. Part of the catalog module's
 * published surface; returns DTOs only.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Catalog", description = "Categories, products and variants (read-only).")
class CatalogController {

    private static final int MAX_PAGE_SIZE = 100;

    private final CatalogService catalogService;

    CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/categories")
    @Operation(summary = "List all categories ordered by sort order.")
    List<CategoryDto> categories() {
        return catalogService.listCategories();
    }

    @GetMapping("/products")
    @Operation(summary = "List products, optionally filtered by category (paginated).")
    PagedResponse<ProductDto> products(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return catalogService.listProducts(categoryId, pageable(page, size));
    }

    @GetMapping("/products/search")
    @Operation(summary = "Full-text + trigram search over product name/description (paginated).")
    PagedResponse<ProductDto> search(
            @RequestParam(name = "q", defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return catalogService.search(q, pageable(page, size));
    }

    @GetMapping("/products/{idOrSlug}")
    @Operation(summary = "Fetch a single product by numeric id or slug.")
    ResponseEntity<ProductDto> product(@PathVariable String idOrSlug) {
        return catalogService.findProduct(idOrSlug)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    private static Pageable pageable(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize);
    }
}
