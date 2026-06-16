package com.townbasket.shared.events;

/**
 * Published by {@code orders} when an order reaches DELIVERED (after the
 * delivery OTP is verified). Consumed by {@code inventory} to commit the
 * reservation (on_hand -= qty), and by {@code notifications}.
 */
public record OrderDelivered(Long orderId, Long storeId) {
}
