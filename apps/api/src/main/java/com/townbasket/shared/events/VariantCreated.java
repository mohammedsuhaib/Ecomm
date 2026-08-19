package com.townbasket.shared.events;

/**
 * Published by {@code catalog} when a product variant is created (single add
 * or bulk import). Consumed by {@code inventory} to open a zero-quantity stock
 * row, so every sellable variant appears in the stock list from birth instead
 * of being invisible until a manual correction.
 */
public record VariantCreated(Long variantId) {
}
