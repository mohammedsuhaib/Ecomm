package com.townbasket.inventory;

import com.townbasket.shared.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin inventory REST API under {@code /api/v1/admin/inventory}.
 * Secured by the existing {@code STORE_STAFF | ADMIN} rule on
 * {@code /api/v1/admin/**} in {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/api/v1/admin/inventory")
@Tag(name = "Admin Inventory", description = "Stock level management and physical-count corrections.")
class AdminInventoryController {

    private static final int MAX_PAGE_SIZE = 200;

    private final AdminInventoryService adminInventoryService;

    AdminInventoryController(AdminInventoryService adminInventoryService) {
        this.adminInventoryService = adminInventoryService;
    }

    @GetMapping("/stock")
    @Operation(summary = "Paged list of all stock levels with product/variant names.")
    PagedResponse<StockLevelDto> list(
            @RequestParam(defaultValue = "1") Long storeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        return adminInventoryService.listStockLevels(storeId, safePage, safeSize);
    }

    @PostMapping("/stock/{variantId}/correction")
    @Operation(summary = "Set on_hand to an absolute count (physical inventory count).")
    ResponseEntity<Void> correct(
            @PathVariable Long variantId,
            @RequestParam(defaultValue = "1") Long storeId,
            @RequestBody StockCorrectionRequest request) {
        adminInventoryService.correctStock(storeId, variantId, request.newOnHand(), request.reason());
        return ResponseEntity.noContent().build();
    }
}
