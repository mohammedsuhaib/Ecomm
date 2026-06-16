package com.townbasket.shared.events;

/**
 * Published by {@code orders} when an order is cancelled (pre-delivery).
 * Consumed by {@code inventory} to release the reservation, and by
 * {@code notifications}.
 */
public record OrderCancelled(Long orderId, Long storeId, String reason) {
}
