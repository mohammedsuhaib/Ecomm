package com.townbasket.notifications;

import com.townbasket.notifications.internal.SseRegistry;
import com.townbasket.orders.OrderService;
import com.townbasket.shared.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Server-Sent Events endpoints (core notification channel, ARCHITECTURE §3.8):
 * the customer order-tracking stream and the admin order-queue stream. Backed by
 * an in-memory emitter registry; order events are pushed by
 * {@code NotificationEventListener}. CORS for {@code /api/**} (incl. SSE) is
 * owned by the security layer ({@code SecurityConfig}). Both streams are
 * AUTHENTICATED and accept the access token via the {@code ?token=} query param
 * (EventSource can't set headers): the admin stream is staff/admin-only, and the
 * per-order stream — keyed by the enumerable numeric id — is owner-only (a
 * non-owner gets 404, so order activity can't be watched by id-guessing).
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Notifications", description = "Live SSE streams for order tracking and the admin queue.")
class NotificationStreamController {

    private final SseRegistry registry;
    private final OrderService orderService;

    NotificationStreamController(SseRegistry registry, OrderService orderService) {
        this.registry = registry;
        this.orderService = orderService;
    }

    @GetMapping(path = "/orders/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Subscribe to an order's live status updates (SSE; AUTHENTICATED, owner only).")
    SseEmitter orderStream(@PathVariable Long id, @AuthenticationPrincipal Long userId) {
        if (!orderService.isOwnedBy(id, userId)) {
            throw new ResourceNotFoundException("Order not found");
        }
        return registry.subscribeToOrder(id);
    }

    @GetMapping(path = "/admin/orders/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Subscribe to the admin queue: new orders + transitions (SSE).")
    SseEmitter adminStream() {
        return registry.subscribeToAdmin();
    }
}
