package com.townbasket.delivery;

import com.townbasket.orders.AgentDaySummary;
import com.townbasket.orders.OrderDto;
import com.townbasket.orders.OrderService;
import com.townbasket.orders.TransitionRequest;
import com.townbasket.shared.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Delivery-agent REST API under {@code /api/v1/delivery}.
 *
 * <p>Secured to {@code DELIVERY_AGENT} and {@code ADMIN} by {@code SecurityConfig}.
 * Scoped to the <strong>calling agent's own assignments</strong>: the queue lists
 * only orders assigned to them and {@code deliver} rejects orders that are not.
 * An {@code ADMIN} caller is treated as a dispatcher — it sees the whole queue
 * and may confirm any delivery (override). The delivery OTP is never returned to
 * the agent; they collect it from the customer at handover.
 */
@RestController
@RequestMapping("/api/v1/delivery")
@Tag(name = "Delivery", description = "Delivery-agent order queue and proof-of-delivery confirmation.")
class DeliveryController {

    private static final int MAX_PAGE_SIZE = 100;

    private final OrderService orderService;
    /** The store's clock (Asia/Kolkata): "today" for the rider's tally is the store day. */
    private final Clock clock;

    DeliveryController(OrderService orderService, Clock clock) {
        this.orderService = orderService;
        this.clock = clock;
    }

    /**
     * GET /api/v1/delivery/orders — the caller's assigned orders in a given
     * status, defaulting to OUT_FOR_DELIVERY (their active delivery queue).
     * Passing {@code status=DELIVERED} lets agents review completed deliveries.
     * An ADMIN sees the whole store queue (dispatcher view).
     */
    @GetMapping("/orders")
    @Operation(summary = "My delivery queue — assigned orders, default OUT_FOR_DELIVERY (ADMIN sees all).")
    PagedResponse<OrderDto> queue(
            @RequestParam(defaultValue = "OUT_FOR_DELIVERY") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal Long userId) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(safePage, safeSize);
        return isAdmin()
                ? orderService.listOrders(status, pageable)
                : orderService.listAgentOrders(userId, status, pageable);
    }

    /**
     * GET /api/v1/delivery/summary — today's tally (store day, IST): orders
     * delivered and Pay-on-Delivery cash collected.
     *
     * <p>Scoped like the queue above, and for the same reason. A RIDER gets
     * their own figures, which is what they settle up against. An ADMIN gets
     * the WHOLE STORE's — every delivery made today whoever made it, and all
     * the cash together.
     *
     * <p>It used to return the caller's own figures even for an admin, on the
     * reasoning that the money is in one rider's pocket. In practice that made
     * the dispatcher view lie: an admin can never be assigned an order
     * ({@code assignAgent} requires an active DELIVERY_AGENT), so the strip was
     * structurally ₹0 while the list beside it showed the store's deliveries —
     * reading as "the store collected nothing today". The store total is the
     * number an admin opening this app actually wants.
     */
    @GetMapping("/summary")
    @Operation(summary = "Today's deliveries and cash collected — the caller's own, or the whole store for an ADMIN.")
    AgentDaySummary summary(@AuthenticationPrincipal Long userId) {
        LocalDate today = LocalDate.now(clock);
        return isAdmin()
                ? orderService.storeDaySummary(today)
                : orderService.agentDaySummary(userId, today);
    }

    /**
     * POST /api/v1/delivery/orders/{id}/deliver — confirm delivery by submitting
     * the customer's delivery OTP. The agent may only deliver an order assigned
     * to them; an ADMIN may confirm any order.
     */
    @PostMapping("/orders/{id}/deliver")
    @Operation(summary = "Confirm delivery with the customer's OTP (must be assigned to you; ADMIN may override).")
    OrderDto deliver(@PathVariable Long id, @RequestBody DeliverRequest request,
                     @AuthenticationPrincipal Long userId) {
        if (isAdmin()) {
            return orderService.transition(id, new TransitionRequest("DELIVERED", request.otp(), null));
        }
        return orderService.confirmDelivery(id, userId, request.otp());
    }

    /**
     * POST /api/v1/delivery/orders/{id}/fail — the rider could not complete the
     * delivery. Records the reason and parks the order in DELIVERY_FAILED for
     * staff to re-dispatch or cancel; the stock reservation is kept because the
     * goods are still in the rider's bag. Must be assigned to the caller (ADMIN
     * may record a failure on any order).
     */
    @PostMapping("/orders/{id}/fail")
    @Operation(summary = "Report a failed delivery attempt with a reason (must be assigned to you; ADMIN may override).")
    OrderDto fail(@PathVariable Long id, @RequestBody FailDeliveryRequest request,
                  @AuthenticationPrincipal Long userId) {
        if (isAdmin()) {
            return orderService.transition(id, new TransitionRequest("DELIVERY_FAILED", null, request.reason()));
        }
        return orderService.failDelivery(id, userId, request.reason());
    }

    private static boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}
