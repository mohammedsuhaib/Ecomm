package com.townbasket.orders;

import com.townbasket.cart.CartDto;
import com.townbasket.shared.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
 * <p>The whole surface is AUTHENTICATED: {@code POST /orders} (login required —
 * no guest checkout), {@code /orders/mine}, {@code /orders/&#42;/reorder}, and the
 * {@code /orders/track/&#42;&#42;} tracking endpoints, which are additionally
 * owner-scoped — the unguessable token alone is no longer enough to read an
 * order; the caller must be the account that placed it (a non-owner gets 404,
 * never a confirming 403). The user id is the security principal (a plain
 * {@code Long} set by the JWT filter); orders does not depend on the identity
 * module for this.
 */
@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders", description = "Idempotent checkout, order tracking, history and reorder.")
class OrderController {

    private static final int MAX_PAGE_SIZE = 100;

    private final OrderService orderService;
    private final InvoiceService invoiceService;

    OrderController(OrderService orderService, InvoiceService invoiceService) {
        this.orderService = orderService;
        this.invoiceService = invoiceService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Place an order from a cart (idempotent; AUTHENTICATED — login required, ties to the caller).")
    OrderDto placeOrder(
            @RequestBody PlaceOrderRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal Long userId) {
        // Login required (no guest checkout) — the security config rejects anonymous
        // POSTs with 401, so a user id is always present here.
        return orderService.placeOrder(request, idempotencyKey, userId);
    }

    @GetMapping("/mine")
    @Operation(summary = "List the caller's own orders, newest first (AUTHENTICATED).")
    PagedResponse<OrderDto> myOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Long userId) {
        return orderService.listUserOrders(userId, pageable(page, size));
    }

    @GetMapping("/track/{token}")
    @Operation(summary = "Fetch an order by its tracking token (AUTHENTICATED, owner only — non-owners get 404).")
    ResponseEntity<OrderDto> trackOrder(@PathVariable UUID token, @AuthenticationPrincipal Long userId) {
        return orderService.getOrderByToken(token, userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/track/{token}/cancel")
    @Operation(summary = "Customer self-service cancel — within 1 minute of placing, before packing (AUTHENTICATED, owner only).")
    OrderDto cancelByToken(@PathVariable UUID token, @AuthenticationPrincipal Long userId) {
        return orderService.cancelByToken(token, userId);
    }

    @GetMapping("/track/{token}/invoice.pdf")
    @Operation(summary = "Download a PDF invoice for an order (AUTHENTICATED, owner only — non-owners get 404).")
    ResponseEntity<byte[]> invoice(@PathVariable UUID token, @AuthenticationPrincipal Long userId) {
        return orderService.getOrderByToken(token, userId)
                .map(order -> {
                    byte[] pdf = invoiceService.renderInvoicePdf(order);
                    return ResponseEntity.ok()
                            .contentType(MediaType.APPLICATION_PDF)
                            .header(HttpHeaders.CONTENT_DISPOSITION,
                                    "attachment; filename=\"townbasket-invoice-" + order.id() + ".pdf\"")
                            .body(pdf);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/reorder")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new cart from a past order's still-available items (AUTHENTICATED).")
    CartDto reorder(@PathVariable Long id, @AuthenticationPrincipal Long userId) {
        return orderService.reorder(id, userId);
    }

    private static Pageable pageable(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize);
    }
}
