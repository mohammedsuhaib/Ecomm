package com.townbasket.inventory;

import java.math.BigDecimal;

/** Stock level for one variant at a store — admin view includes product/variant names. */
public record StockLevelDto(
        Long id,
        Long variantId,
        Long productId,
        String productName,
        String variantLabel,
        BigDecimal sellingPrice,
        int onHand,
        int reserved,
        int available,
        int lowStockThreshold) {}
