package com.townbasket.orders;

import com.townbasket.cart.CartDto;
import com.townbasket.shared.PagedResponse;
import java.util.List;
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
     * marks the cart checked out, and publishes {@code OrderPlaced}. The order
     * is left PLACED: staff confirm it from the admin queue via
     * {@link #transition}, which is what publishes {@code OrderConfirmed}.
     *
     * <p>{@code userId} ties the order to a logged-in customer when a valid
     * Bearer token was present; it is {@code null} for a guest order (the
     * endpoint is PUBLIC).
     */
    OrderDto placeOrder(PlaceOrderRequest request, String idempotencyKey, Long userId);

    /**
     * Customer-facing fetch by unguessable tracking token (confirmation +
     * tracking), AUTHENTICATED and owner-scoped: the order is returned only when
     * {@code userId} matches the account that placed it (an order with no owner —
     * a legacy guest order — is returned to nobody here; staff read those via the
     * admin listing). A non-owner gets the same empty result as an unknown token,
     * so the response never confirms the order exists. The numeric id is never
     * accepted here, so order details cannot be harvested by enumerating
     * sequential ids. The delivery OTP is included only while the order is
     * OUT_FOR_DELIVERY.
     */
    Optional<OrderDto> getOrderByToken(UUID trackingToken, Long userId);

    /**
     * Customer self-service cancellation by tracking token (AUTHENTICATED and
     * owner-scoped, same access model as {@link #getOrderByToken}). Allowed only
     * within the published cancellation window (1 minute of placing — refund
     * policy) and while the order is still PLACED/CONFIRMED. Cancelling releases
     * the stock reservation via the normal CANCELLED transition events.
     * Idempotent: an already-cancelled order is returned as-is.
     *
     * @throws com.townbasket.shared.ResourceNotFoundException if the token is
     *     unknown OR the order is not owned by {@code userId} (indistinguishable
     *     on purpose)
     * @throws com.townbasket.shared.BusinessRuleException if the window has
     *     passed or fulfilment has already started (mapped to 422)
     */
    OrderDto cancelByToken(UUID trackingToken, Long userId);

    /**
     * Issue the GST invoice for an order and return it with the invoice number
     * set (AUTHENTICATED and owner-scoped, same access model as
     * {@link #getOrderByToken}).
     *
     * <p>An invoice is issued only once the order has been <strong>DELIVERED</strong>
     * — it records a supply that has actually taken place, and until handover
     * the goods are still the store's. The number is taken from a
     * per-financial-year consecutive series (CGST Rule 46(b)) on the FIRST call
     * and stamped on the order with the issue timestamp; later calls return the
     * same number, so a re-download reproduces the same document instead of
     * billing one supply twice. An already-issued invoice is always served,
     * whatever the order's status.
     *
     * @throws com.townbasket.shared.ResourceNotFoundException if the token is
     *     unknown or the order is not owned by {@code userId}
     * @throws com.townbasket.shared.BusinessRuleException if no invoice has been
     *     issued yet and the order is not delivered — whether it is still in
     *     flight or was cancelled (mapped to 422; the message distinguishes the
     *     two)
     */
    OrderDto issueInvoice(UUID trackingToken, Long userId);

    /**
     * Whether the order belongs to {@code userId} — the ownership gate for the
     * per-order SSE tracking stream (which is keyed by the enumerable numeric
     * id, so it must not leak activity to non-owners). False for a null user,
     * an unknown order, or an order with no owner.
     */
    boolean isOwnedBy(Long orderId, Long userId);

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
     * Admin listing with a free-text search: {@code q} matches the order number,
     * any part of the phone, or any part of the customer name (case-insensitive),
     * applied in SQL so the pager describes the matches. Null/blank {@code q}
     * behaves like {@link #listOrders(String, Pageable)}.
     */
    PagedResponse<OrderDto> listOrders(String status, String q, Pageable pageable);

    /**
     * Admin reporting: delivered-order counts and summed order value per agent
     * per date, newest date first. Only DELIVERED orders with an assigned
     * agent are counted.
     *
     * @param days how far back to report, in days. Bounded deliberately: the
     *     unwindowed version returned one row per agent per delivery date for
     *     the life of the store and rescanned every order event to do it, so
     *     both the response and the work grew without limit. The caller clamps
     *     the value.
     */
    List<AgentDeliveryStat> deliveryStatsByAgent(int days);

    /**
     * Admin: apply a state-machine transition. Enforces the allowed transitions;
     * {@code DELIVERED} requires a matching delivery OTP. Publishes
     * {@code OrderStatusChanged}, plus {@code OrderConfirmed} when staff accept
     * a PLACED order and {@code OrderDelivered} / {@code OrderCancelled} for the
     * terminal transitions (which drive stock commit / release).
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
     * Delivery: the rider's own tally for one store day — orders they
     * delivered and the Pay-on-Delivery cash they collected doing it. Zeros,
     * never null, for a day with nothing. {@code day} is a calendar date in the
     * store's zone (the application {@link java.time.Clock}); the caller
     * normally passes today.
     */
    AgentDaySummary agentDaySummary(Long agentId, java.time.LocalDate day);

    /**
     * Delivery, dispatcher view: the same tally for the WHOLE STORE on one
     * store day — every delivery made, whoever made it, and all the
     * Pay-on-Delivery cash together. What an ADMIN signing into the delivery
     * app is asking for; a rider must never be given this, since it is not
     * theirs to reconcile.
     *
     * <p>Shares {@link AgentDaySummary}'s shape so the app renders one strip
     * either way — the numbers differ in scope, not in meaning. Zeros, never
     * null, for a day with nothing.
     */
    AgentDaySummary storeDaySummary(java.time.LocalDate day);

    /**
     * Delivery: confirm delivery by OTP, verifying the order is assigned to this
     * agent first.
     *
     * @throws org.springframework.security.access.AccessDeniedException if the order is not assigned to {@code agentId}
     */
    OrderDto confirmDelivery(Long orderId, Long agentId, String otp);

    /**
     * The assigned agent reports that a delivery attempt failed (customer
     * unreachable, wrong address, refused at the door): OUT_FOR_DELIVERY →
     * DELIVERY_FAILED with the reason on the timeline. Reserved stock is NOT
     * released — the goods are still with the rider — staff later re-dispatch
     * or cancel. Rejects orders not assigned to {@code agentId}.
     */
    OrderDto failDelivery(Long orderId, Long agentId, String reason);
}
