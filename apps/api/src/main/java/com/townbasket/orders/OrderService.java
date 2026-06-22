package com.townbasket.orders;

import com.townbasket.shared.PagedResponse;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

/**
 * Published API of the orders module — the checkout orchestrator and the
 * staff-driven state machine.
 *
 * <p>Checkout synchronously calls the public services of {@code cart},
 * {@code serviceability}, {@code inventory} and {@code payments}; those modules
 * never call back into orders synchronously — they react to the domain events
 * orders publishes (in {@code shared}).
 */
public interface OrderService {

    /**
     * Place an order from a cart. Idempotent on the idempotency key: a retry with
     * the same key returns the originally-created order rather than placing a new
     * one. Validates serviceability + minimum order value + stock, reserves
     * stock, snapshots prices (and COGS), charges payment, persists the order,
     * marks the cart checked out, and publishes {@code OrderPlaced} +
     * {@code OrderConfirmed}.
     */
    OrderDto placeOrder(PlaceOrderRequest request, String idempotencyKey);

    /**
     * Customer-facing fetch by unguessable tracking token (confirmation +
     * tracking). The numeric id is never accepted here, so order details cannot
     * be harvested by enumerating sequential ids. The delivery OTP is included
     * only while the order is OUT_FOR_DELIVERY.
     */
    Optional<OrderDto> getOrderByToken(UUID trackingToken);

    /** Admin: list orders newest-first, optionally filtered by status. */
    PagedResponse<OrderDto> listOrders(String status, Pageable pageable);

    /**
     * Admin: apply a state-machine transition. Enforces the allowed transitions;
     * {@code DELIVERED} requires a matching delivery OTP. Publishes
     * {@code OrderStatusChanged}, plus {@code OrderDelivered} / {@code OrderCancelled}
     * for the terminal transitions (which drive stock commit / release).
     */
    OrderDto transition(Long orderId, TransitionRequest request);
}
