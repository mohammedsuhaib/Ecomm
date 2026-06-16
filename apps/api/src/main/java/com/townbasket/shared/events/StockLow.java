package com.townbasket.shared.events;

/**
 * Published by {@code inventory} when available stock
 * ({@code on_hand - reserved}) for a variant drops to or below its low-stock
 * threshold. Consumed by {@code notifications} (admin alert).
 */
public record StockLow(
        Long storeId,
        Long variantId,
        int available,
        int threshold) {
}
