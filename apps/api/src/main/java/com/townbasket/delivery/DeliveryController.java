package com.townbasket.delivery;

import com.townbasket.orders.OrderDto;
import com.townbasket.orders.OrderService;
import com.townbasket.orders.TransitionRequest;
import com.townbasket.shared.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
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
 * Exposes a narrower, role-appropriate surface than the full admin API:
 * delivery agents can see the out-for-delivery queue and confirm deliveries
 * via OTP — nothing else.
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
     * GET /api/v1/delivery/orders — paged list of orders in a given status,
     * defaulting to OUT_FOR_DELIVERY (the active delivery queue).
     * Passing {@code status=DELIVERED} lets agents review completed deliveries.
     */
    @GetMapping("/orders")
    @Operation(summary = "Delivery queue — defaults to OUT_FOR_DELIVERY, filterable by status.")
    PagedResponse<OrderDto> queue(
            @RequestParam(defaultValue = "OUT_FOR_DELIVERY") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return orderService.listOrders(status, PageRequest.of(safePage, safeSize));
    }

    /**
     * POST /api/v1/delivery/orders/{id}/deliver — confirm delivery by submitting
     * the customer's delivery OTP. Delegates to the existing order state machine
     * ({@code DELIVERED} transition with OTP validation).
     */
    @PostMapping("/orders/{id}/deliver")
    @Operation(summary = "Confirm delivery with the customer's OTP code.")
    OrderDto deliver(@PathVariable Long id, @RequestBody DeliverRequest request) {
        return orderService.transition(id,
                new TransitionRequest("DELIVERED", request.otp(), null));
    }
}
