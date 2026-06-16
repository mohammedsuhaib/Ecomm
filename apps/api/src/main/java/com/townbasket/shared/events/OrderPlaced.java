package com.townbasket.shared.events;

import java.util.List;

/**
 * Published by {@code orders} when a new order is persisted (after stock has
 * been reserved). Carried in the OPEN {@code shared} module so any module may
 * react to it. Cross-module references are by id (Long) only.
 *
 * <p>Lines carry only {@code variantId}/{@code qty} — enough for inventory to
 * commit/release reservations later. No prices, no COGS.
 */
public record OrderPlaced(
        Long orderId,
        Long storeId,
        List<Line> lines) {

    /** A single reserved line: {@code (variantId, qty)}. */
    public record Line(Long variantId, int qty) {
    }
}
