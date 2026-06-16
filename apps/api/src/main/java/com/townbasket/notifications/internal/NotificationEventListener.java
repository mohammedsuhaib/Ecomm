package com.townbasket.notifications.internal;

import com.townbasket.shared.events.OrderCancelled;
import com.townbasket.shared.events.OrderConfirmed;
import com.townbasket.shared.events.OrderDelivered;
import com.townbasket.shared.events.OrderPlaced;
import com.townbasket.shared.events.OrderStatusChanged;
import java.time.Instant;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Consumes order domain events and fans them out to the live SSE streams (the
 * core notification channel), logging each push. Best-effort: a notification
 * failure never blocks an order transition (the publishing transaction has
 * already committed before these handlers run).
 *
 * <ul>
 *   <li>{@code OrderPlaced} -> admin queue (new order).</li>
 *   <li>{@code OrderConfirmed} -> customer order stream + admin.</li>
 *   <li>{@code OrderStatusChanged} -> customer order stream + admin.</li>
 *   <li>{@code OrderDelivered}/{@code OrderCancelled} -> customer order stream + admin.</li>
 * </ul>
 */
@Component
class NotificationEventListener {

    private static final String CHANNEL_SSE = "SSE";

    private final SseRegistry registry;
    private final NotificationLogRepository log;

    NotificationEventListener(SseRegistry registry, NotificationLogRepository log) {
        this.registry = registry;
        this.log = log;
    }

    @ApplicationModuleListener
    void on(OrderPlaced event) {
        registry.publishToAdmin("order-placed", payload(event.orderId(), "PLACED", "ORDER_PLACED"));
        record(event.orderId(), "ORDER_PLACED");
    }

    @ApplicationModuleListener
    void on(OrderConfirmed event) {
        var p = payload(event.orderId(), "CONFIRMED", "ORDER_CONFIRMED");
        registry.publishToOrder(event.orderId(), "status", p);
        registry.publishToAdmin("order-updated", p);
        record(event.orderId(), "ORDER_CONFIRMED");
    }

    @ApplicationModuleListener
    void on(OrderStatusChanged event) {
        var p = payload(event.orderId(), event.toStatus(), "STATUS_CHANGED");
        registry.publishToOrder(event.orderId(), "status", p);
        registry.publishToAdmin("order-updated", p);
        record(event.orderId(), "STATUS_CHANGED");
    }

    @ApplicationModuleListener
    void on(OrderDelivered event) {
        var p = payload(event.orderId(), "DELIVERED", "ORDER_DELIVERED");
        registry.publishToOrder(event.orderId(), "status", p);
        registry.publishToAdmin("order-updated", p);
        record(event.orderId(), "ORDER_DELIVERED");
    }

    @ApplicationModuleListener
    void on(OrderCancelled event) {
        var p = payload(event.orderId(), "CANCELLED", "ORDER_CANCELLED");
        registry.publishToOrder(event.orderId(), "status", p);
        registry.publishToAdmin("order-updated", p);
        record(event.orderId(), "ORDER_CANCELLED");
    }

    private void record(Long orderId, String type) {
        log.save(new NotificationLogEntity(orderId, CHANNEL_SSE, type));
    }

    private static NotificationPayload payload(Long orderId, String status, String type) {
        return new NotificationPayload(orderId, status, type, Instant.now());
    }

    /** SSE message body delivered to subscribers. */
    record NotificationPayload(Long orderId, String status, String type, Instant at) {
    }
}
