package com.townbasket.orders;

import com.townbasket.cart.CartDto;
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
     *
     * <p>{@code userId} ties the order to a logged-in customer when a valid
     * Bearer token was present; it is {@code null} for a guest order (the
     * endpoint is PUBLIC).
     */
    OrderDto placeOrder(PlaceOrderRequest request, String idempotencyKey, Long userId);

    /**
     * Customer-facing fetch by unguessable tracking token (confirmation +
     * tracking). The numeric id is never accepted here, so order details cannot
     * be harvested by enumerating sequential ids. The delivery OTP is included
     * only while the order is OUT_FOR_DELIVERY.
     */
    Optional<OrderDto> getOrderByToken(UUID trackingToken);

    /**
     * Customer self-service cancellation by tracking token (PUBLIC-by-token,
     * same capability model as {@link #getOrderByToken}). Allowed only within
     * the published cancellation window (1 minute of placing — refund policy)
     * and while the order is still PLACED/CONFIRMED. Cancelling releases the
     * stock reservation via the normal CANCELLED transition events. Idempotent:
     * an already-cancelled order is returned as-is.
     *
     * @throws com.townbasket.shared.ResourceNotFoundException if the token is unknown
     * @throws com.townbasket.shared.BusinessRuleException if the window has
     *     passed or fulfilment has already started (mapped to 422)
     */
    OrderDto cancelByToken(UUID trackingToken);

    /** A customer's own orders, newest first (AUTHENTICATED). */
    PagedResponse<OrderDto> listUserOrders(Long userId, Pageable pageable);

    /**
     * Reorder: create a NEW cart owned by the user, populated from the order's
     * currently catalog-available lines (unavailable lines are skipped). Returns
     * the new cart.
     *
     * @throws com.townbasket.shared.ResourceNotFoundException if the order is missing or not owned by the user
     */
    CartDto reorder(Long orderId, Long userId);

    /** Admin: list orders newest-first, optionally filtered by status. */
    PagedResponse<OrderDto> listOrders(String status, Pageable pageable);

    /**
     * Admin: apply a state-machine transition. Enforces the allowed transitions;
     * {@code DELIVERED} requires a matching delivery OTP. Publishes
     * {@code OrderStatusChanged}, plus {@code OrderDelivered} / {@code OrderCancelled}
     * for the terminal transitions (which drive stock commit / release).
     */
    OrderDto transition(Long orderId, TransitionRequest request);

    /**
     * Admin dispatch: assign an order to a delivery agent (or pass {@code null}
     * to clear the assignment). Rejected once the order is terminal
     * (DELIVERED/CANCELLED).
     */
    OrderDto assignAgent(Long orderId, Long agentId);

    /**
     * Delivery: orders assigned to {@code agentId}, optionally filtered by status
     * (newest first). The delivery OTP is never included (the agent collects it
     * from the customer at handover).
     */
    PagedResponse<OrderDto> listAgentOrders(Long agentId, String status, Pageable pageable);

    /**
     * Delivery: confirm delivery by OTP, verifying the order is assigned to this
     * agent first.
     *
     * @throws org.springframework.security.access.AccessDeniedException if the order is not assigned to {@code agentId}
     */
    OrderDto confirmDelivery(Long orderId, Long agentId, String otp);
}
