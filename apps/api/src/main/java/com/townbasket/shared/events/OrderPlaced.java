package com.townbasket.shared.events;

/**
 * Published by {@code orders} when a new order is persisted (after stock has
 * been reserved). Carried in the OPEN {@code shared} module so any module may
 * react to it. Cross-module references are by id (Long) only.
 */
public record OrderPlaced(
        Long orderId,
        Long storeId) {
}
