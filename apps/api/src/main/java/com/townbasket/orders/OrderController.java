package com.townbasket.orders;

import com.townbasket.cart.CartDto;
import com.townbasket.shared.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customer-facing orders REST API under {@code /api/v1}: idempotent checkout,
 * order fetch (confirmation + tracking), the caller's order history and reorder.
 * Returns {@link OrderDto} / {@link CartDto} only.
 *
 * <p>{@code POST /orders} is AUTHENTICATED (login required — no guest checkout),
 * as are {@code /orders/mine} and {@code /orders/&#42;/reorder}; {@code GET /orders/{id}}
 * stays public for confirmation + tracking. The user id is read from the
 * {@code SecurityContext} principal (a plain {@code Long}); orders does not depend
 * on the identity module for this.
 */
@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders", description = "Idempotent checkout, order tracking, history and reorder.")
class OrderController {

    private static final int MAX_PAGE_SIZE = 100;

    private final OrderService orderService;

    OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Place an order from a cart (idempotent; AUTHENTICATED — login required, ties to the caller).")
    OrderDto placeOrder(
            @RequestBody PlaceOrderRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        // Login required (no guest checkout) — the security config rejects anonymous
        // POSTs with 401, so a user id is always present here.
        return orderService.placeOrder(request, idempotencyKey, requireUserId());
    }

    @GetMapping("/mine")
    @Operation(summary = "List the caller's own orders, newest first (AUTHENTICATED).")
    PagedResponse<OrderDto> myOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return orderService.listUserOrders(requireUserId(), pageable(page, size));
    }

    @GetMapping("/track/{token}")
    @Operation(summary = "Fetch an order by its unguessable tracking token (confirmation + live tracking).")
    ResponseEntity<OrderDto> trackOrder(@PathVariable UUID token) {
        return orderService.getOrderByToken(token)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/reorder")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new cart from a past order's still-available items (AUTHENTICATED).")
    CartDto reorder(@PathVariable Long id) {
        return orderService.reorder(id, requireUserId());
    }

    /** The caller's user id, or {@code null} for an unauthenticated (guest) request. */
    private static Long currentUserIdOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long userId) {
            return userId;
        }
        return null;
    }

    /** The caller's user id; the security config guarantees a token reached these routes. */
    private static Long requireUserId() {
        Long userId = currentUserIdOrNull();
        if (userId == null) {
            throw new IllegalStateException("No authenticated user in security context");
        }
        return userId;
    }

    private static Pageable pageable(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize);
    }
}
