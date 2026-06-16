package com.townbasket.shared.events;

/**
 * Published by {@code orders} once an order reaches CONFIRMED (COD at placement,
 * or UPI after payment succeeds). Consumed by {@code notifications}.
 */
public record OrderConfirmed(Long orderId, Long storeId) {
}
