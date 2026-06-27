package com.townbasket.analytics;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin analytics REST API — all endpoints under {@code /api/v1/admin/analytics}.
 * Secured by the existing {@code STORE_STAFF | ADMIN} rule on {@code /api/v1/admin/**}.
 *
 * <p>All queries run read-only against existing {@code orders.*} and
 * {@code inventory.*} schemas via native SQL; no additional tables required.
 */
@RestController
@RequestMapping("/api/v1/admin/analytics")
@Tag(name = "Admin Analytics", description = "Sales dashboard and low-stock alerts.")
class AnalyticsController {

    private final AnalyticsService analyticsService;

    AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/summary")
    @Operation(summary = "Today's GMV, order counts (IST day boundary), and live pending-queue depth.")
    AnalyticsSummaryDto summary(@RequestParam(defaultValue = "1") Long storeId) {
        return analyticsService.getSummary(storeId);
    }

    @GetMapping("/daily")
    @Operation(summary = "Revenue, order count, and gross profit per day for the last N days (max 90).")
    List<DailySummaryDto> daily(
            @RequestParam(defaultValue = "1") Long storeId,
            @RequestParam(defaultValue = "30") int days) {
        return analyticsService.getDailySummary(storeId, Math.min(days, 90));
    }

    @GetMapping("/top-products")
    @Operation(summary = "Top-selling variants by units sold in the last N days.")
    List<TopProductDto> topProducts(
            @RequestParam(defaultValue = "1") Long storeId,
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "10") int limit) {
        return analyticsService.getTopProducts(storeId, Math.min(days, 90), Math.min(limit, 50));
    }

    @GetMapping("/low-stock")
    @Operation(summary = "Variants at or below their low-stock threshold.")
    List<LowStockDto> lowStock(@RequestParam(defaultValue = "1") Long storeId) {
        return analyticsService.getLowStockItems(storeId);
    }
}
