package com.townbasket.orders;

import com.townbasket.shared.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin orders REST API under {@code /api/v1/admin}: the order queue and
 * one-tap state transitions.
 *
 * <p><strong>Not secured yet</strong> — staff auth/roles arrive in M4. The
 * endpoints are otherwise complete (paged newest-first queue + transition state
 * machine, with the DELIVERED OTP check).
 */
@RestController
@RequestMapping("/api/v1/admin/orders")
@Tag(name = "Admin Orders", description = "Order queue and state transitions (auth in M4).")
class AdminOrderController {

    private static final int MAX_PAGE_SIZE = 100;

    private final OrderService orderService;

    AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    @Operation(summary = "List orders newest-first, optionally filtered by status (paginated).")
    PagedResponse<OrderDto> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return orderService.listOrders(status, pageable(page, size));
    }

    @PostMapping("/{id}/transitions")
    @Operation(summary = "Apply a state-machine transition (DELIVERED requires the delivery OTP).")
    OrderDto transition(@PathVariable Long id, @RequestBody TransitionRequest request) {
        return orderService.transition(id, request);
    }

    @PostMapping("/{id}/assign")
    @Operation(summary = "Assign the order to a delivery agent (agentId=null clears the assignment).")
    OrderDto assign(@PathVariable Long id, @RequestBody AssignAgentRequest request) {
        return orderService.assignAgent(id, request.agentId());
    }

    private static Pageable pageable(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize);
    }
}
