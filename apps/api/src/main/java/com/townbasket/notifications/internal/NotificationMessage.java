package com.townbasket.notifications.internal;

import java.time.Instant;

/**
 * One notification, in channel-neutral form. Each {@link NotificationChannel}
 * renders it however its medium requires (an SSE frame, a Web Push payload,
 * later an SMS/WhatsApp template).
 *
 * @param orderId         the order this is about
 * @param recipientUserId the person to reach off-page — the customer, or the
 *                        rider for {@link Audience#AGENT} messages;
 *                        {@code null} for staff-queue messages and for legacy
 *                        orders with no account, in which case user-targeted
 *                        channels skip the message
 * @param audience        who this is for; channels use it to decide whether the
 *                        message is theirs to deliver
 * @param type            machine-readable kind (ORDER_PLACED, STATUS_CHANGED, …)
 * @param status          the order status this message reports
 * @param title           short headline for channels that show one
 * @param body            one-line human-readable detail
 * @param url             relative deep link to open when tapped
 */
record NotificationMessage(
        Long orderId,
        Long recipientUserId,
        Audience audience,
        String type,
        String status,
        String title,
        String body,
        String url,
        Instant at) {

    /** Who a message is aimed at. */
    enum Audience {
        /** The customer who placed the order (also refreshes the staff queue). */
        CUSTOMER,
        /** Store staff watching the order queue. */
        ADMIN,
        /** The delivery rider the order is assigned to. */
        AGENT
    }

    /** Customer-facing update; also refreshes the live streams staff watch. */
    static NotificationMessage forCustomer(Long orderId, Long userId, String type, String status,
                                           String title, String body, String url) {
        return new NotificationMessage(orderId, userId, Audience.CUSTOMER, type, status,
                title, body, url, Instant.now());
    }

    /** Staff-only message (no individual recipient). */
    static NotificationMessage forAdmin(Long orderId, String type, String status, String title, String body) {
        return new NotificationMessage(orderId, null, Audience.ADMIN, type, status,
                title, body, null, Instant.now());
    }

    /**
     * Rider-only message. There is no rider SSE stream — the delivery app polls
     * — so these travel by push alone; the SSE channel deliberately ignores them
     * rather than leaking a rider's job into the customer's tracking stream.
     */
    static NotificationMessage forAgent(Long orderId, Long agentId, String type, String status,
                                        String title, String body) {
        return new NotificationMessage(orderId, agentId, Audience.AGENT, type, status,
                title, body, "/", Instant.now());
    }
}
