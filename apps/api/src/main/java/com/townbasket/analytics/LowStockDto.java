package com.townbasket.analytics;

/** A variant at or below its low-stock threshold — shown on the analytics dashboard. */
public record LowStockDto(
        Long variantId,
        Long productId,
        String productName,
        String variantLabel,
        int available,
        int threshold) {}
