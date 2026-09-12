package com.townbasket.orders.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The state machine's rules, offline. The DELIVERY_FAILED branch is the new one. */
class OrderStatusTest {

    @Test
    void aFailedAttemptIsOnlyReachableFromOutForDelivery() {
        assertThat(OrderStatus.OUT_FOR_DELIVERY.canTransitionTo(OrderStatus.DELIVERY_FAILED)).isTrue();
        for (OrderStatus s : new OrderStatus[] {
                OrderStatus.PLACED, OrderStatus.CONFIRMED, OrderStatus.PACKING,
                OrderStatus.DELIVERED, OrderStatus.CANCELLED, OrderStatus.DELIVERY_FAILED}) {
            assertThat(s.canTransitionTo(OrderStatus.DELIVERY_FAILED))
                    .as("%s -> DELIVERY_FAILED", s).isFalse();
        }
    }

    @Test
    void aFailedAttemptCanBeRetriedOrCancelledAndNothingElse() {
        assertThat(OrderStatus.DELIVERY_FAILED.allowedNext())
                .containsExactlyInAnyOrder(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.CANCELLED);
        // In particular it can never be marked DELIVERED without going back out.
        assertThat(OrderStatus.DELIVERY_FAILED.canTransitionTo(OrderStatus.DELIVERED)).isFalse();
    }

    @Test
    void theHappyPathIsUnchanged() {
        assertThat(OrderStatus.PLACED.allowedNext()).containsExactlyInAnyOrder(OrderStatus.CONFIRMED, OrderStatus.CANCELLED);
        assertThat(OrderStatus.CONFIRMED.allowedNext()).containsExactlyInAnyOrder(OrderStatus.PACKING, OrderStatus.CANCELLED);
        assertThat(OrderStatus.PACKING.allowedNext()).containsExactlyInAnyOrder(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.CANCELLED);
        assertThat(OrderStatus.OUT_FOR_DELIVERY.allowedNext())
                .containsExactlyInAnyOrder(OrderStatus.DELIVERED, OrderStatus.DELIVERY_FAILED, OrderStatus.CANCELLED);
    }

    @Test
    void terminalStatesStayTerminal() {
        assertThat(OrderStatus.DELIVERED.allowedNext()).isEmpty();
        assertThat(OrderStatus.CANCELLED.allowedNext()).isEmpty();
    }
}
