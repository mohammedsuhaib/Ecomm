package com.townbasket.inventory;

/**
 * A single line to reserve: {@code (variantId, qty)}. Part of the inventory
 * module's public API; cross-module references are by id only.
 */
public record ReservationLine(Long variantId, int qty) {
}
