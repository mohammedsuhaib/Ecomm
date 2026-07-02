package com.townbasket.delivery;

import com.townbasket.orders.OrderDto;
import com.townbasket.orders.OrderService;
import com.townbasket.orders.TransitionRequest;
import com.townbasket.shared.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
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

    DeliveryController(OrderService orderService) {
        this.orderService = orderService;
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
            @RequestParam(defaultValue = "50") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(safePage, safeSize);
        return isAdmin()
                ? orderService.listOrders(status, pageable)
                : orderService.listAgentOrders(currentUserId(), status, pageable);
    }

    /**
     * POST /api/v1/delivery/orders/{id}/deliver — confirm delivery by submitting
     * the customer's delivery OTP. The agent may only deliver an order assigned
     * to them; an ADMIN may confirm any order.
     */
    @PostMapping("/orders/{id}/deliver")
    @Operation(summary = "Confirm delivery with the customer's OTP (must be assigned to you; ADMIN may override).")
    OrderDto deliver(@PathVariable Long id, @RequestBody DeliverRequest request) {
        if (isAdmin()) {
            return orderService.transition(id, new TransitionRequest("DELIVERED", request.otp(), null));
        }
        return orderService.confirmDelivery(id, currentUserId(), request.otp());
    }

    /** The authenticated caller's user id (the security layer guarantees one here). */
    private static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long userId) {
            return userId;
        }
        throw new IllegalStateException("No authenticated user in security context");
    }

    private static boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}
