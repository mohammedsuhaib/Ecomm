package com.townbasket.notifications.internal;

import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Core in-app channel: pushes to the live SSE streams that drive the customer
 * tracking page and the admin order queue. Always enabled — it needs no
 * configuration and no third party.
 *
 * <p>The frame shape ({@code orderId, status, type, at} under the event names
 * {@code status} / {@code order-placed} / {@code order-updated}) is the
 * published contract the storefront and admin clients already parse, so it is
 * kept byte-for-byte compatible.
 */
@Component
class SseNotificationChannel implements NotificationChannel {

    static final String NAME = "SSE";

    private final SseRegistry registry;

    SseNotificationChannel(SseRegistry registry) {
        this.registry = registry;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean send(NotificationMessage message) {
        // Rider messages have no SSE stream to travel on (the delivery app
        // polls), and must not be replayed into the customer's tracking stream.
        if (message.audience() == NotificationMessage.Audience.AGENT) {
            return false;
        }
        SsePayload payload = new SsePayload(
                message.orderId(), message.status(), message.type(), message.at());
        // A brand-new order is an admin-queue arrival; everything else is an
        // update to an order someone may be tracking.
        if ("ORDER_PLACED".equals(message.type())) {
            registry.publishToAdmin("order-placed", payload);
        } else {
            registry.publishToOrder(message.orderId(), "status", payload);
            registry.publishToAdmin("order-updated", payload);
        }
        return true;
    }

    /** SSE message body delivered to subscribers. */
    record SsePayload(Long orderId, String status, String type, Instant at) {
    }
}
