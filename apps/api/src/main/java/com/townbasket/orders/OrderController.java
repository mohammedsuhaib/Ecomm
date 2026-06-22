package com.townbasket.orders;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customer-facing orders REST API under {@code /api/v1}: idempotent checkout and
 * order fetch (confirmation + tracking). Part of the orders module's published
 * surface; returns {@link OrderDto} only.
 */
@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders", description = "Idempotent checkout and order tracking.")
class OrderController {

    private final OrderService orderService;

    OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Place an order from a cart (idempotent on the Idempotency-Key header or field).")
    OrderDto placeOrder(
            @RequestBody PlaceOrderRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        return orderService.placeOrder(request, idempotencyKey);
    }

    @GetMapping("/track/{token}")
    @Operation(summary = "Fetch an order by its unguessable tracking token (confirmation + live tracking).")
    ResponseEntity<OrderDto> trackOrder(@PathVariable UUID token) {
        return orderService.getOrderByToken(token)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
