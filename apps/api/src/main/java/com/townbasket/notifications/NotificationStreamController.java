package com.townbasket.notifications;

import com.townbasket.notifications.internal.SseRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
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
 * owned by the security layer ({@code SecurityConfig}). The admin stream
 * ({@code /admin/orders/stream}) is staff/admin-only and accepts the access
 * token via the {@code ?token=} query param (EventSource can't set headers).
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Notifications", description = "Live SSE streams for order tracking and the admin queue.")
class NotificationStreamController {

    private final SseRegistry registry;

    NotificationStreamController(SseRegistry registry) {
        this.registry = registry;
    }

    @GetMapping(path = "/orders/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Subscribe to an order's live status updates (SSE).")
    SseEmitter orderStream(@PathVariable Long id) {
        return registry.subscribeToOrder(id);
    }

    @GetMapping(path = "/admin/orders/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Subscribe to the admin queue: new orders + transitions (SSE).")
    SseEmitter adminStream() {
        return registry.subscribeToAdmin();
    }
}
