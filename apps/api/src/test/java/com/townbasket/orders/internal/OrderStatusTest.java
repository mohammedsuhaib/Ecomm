package com.townbasket.orders.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The state machine's rules, offline. */
class OrderStatusTest {

    @Test
    void aFailedAttemptIsOnlyReachableFromOutForDelivery() {
        assertThat(OrderStatus.OUT_FOR_DELIVERY.canTransitionTo(OrderStatus.DELIVERY_FAILED)).isTrue();
        for (OrderStatus s : new OrderStatus[] {
                OrderStatus.PLACED, OrderStatus.CONFIRMED, OrderStatus.PACKING,
                OrderStatus.READY_FOR_DELIVERY, OrderStatus.DELIVERED, OrderStatus.CANCELLED,
                OrderStatus.DELIVERY_FAILED}) {
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
    void theHappyPathRunsThroughReadyForDelivery() {
        assertThat(OrderStatus.PLACED.allowedNext()).containsExactlyInAnyOrder(OrderStatus.CONFIRMED, OrderStatus.CANCELLED);
        assertThat(OrderStatus.CONFIRMED.allowedNext()).containsExactlyInAnyOrder(OrderStatus.PACKING, OrderStatus.CANCELLED);
        assertThat(OrderStatus.PACKING.allowedNext())
                .containsExactlyInAnyOrder(OrderStatus.READY_FOR_DELIVERY, OrderStatus.CANCELLED);
        assertThat(OrderStatus.READY_FOR_DELIVERY.allowedNext())
                .containsExactlyInAnyOrder(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.CANCELLED);
        assertThat(OrderStatus.OUT_FOR_DELIVERY.allowedNext())
                .containsExactlyInAnyOrder(OrderStatus.DELIVERED, OrderStatus.DELIVERY_FAILED, OrderStatus.CANCELLED);
    }

    @Test
    void packingCannotSkipStraightOntoTheRoad() {
        // The whole point of READY_FOR_DELIVERY: "packed" and "on a bike" are
        // two different facts, and only the second one starts the customer's
        // tracking map, their ETA and their delivery code. Letting the packer's
        // last click do both is what used to conflate them.
        assertThat(OrderStatus.PACKING.canTransitionTo(OrderStatus.OUT_FOR_DELIVERY)).isFalse();
        assertThat(OrderStatus.PACKING.canTransitionTo(OrderStatus.DELIVERED)).isFalse();
    }

    @Test
    void anOrderWaitingForARiderCanStillBeCancelled() {
        // Nothing has left the shop, so a cancellation here is ordinary: the
        // bag goes back on the shelves and the reservation is released.
        assertThat(OrderStatus.READY_FOR_DELIVERY.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
        // But it cannot be delivered from the counter, with or without a code.
        assertThat(OrderStatus.READY_FOR_DELIVERY.canTransitionTo(OrderStatus.DELIVERED)).isFalse();
    }

    @Test
    void terminalStatesStayTerminal() {
        assertThat(OrderStatus.DELIVERED.allowedNext()).isEmpty();
        assertThat(OrderStatus.CANCELLED.allowedNext()).isEmpty();
    }
}
