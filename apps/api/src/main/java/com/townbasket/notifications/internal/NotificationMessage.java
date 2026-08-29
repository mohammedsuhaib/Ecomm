package com.townbasket.notifications.internal;

import java.time.Instant;

/**
 * One notification, in channel-neutral form. Each {@link NotificationChannel}
 * renders it however its medium requires (an SSE frame, a Web Push payload,
 * later an SMS/WhatsApp template).
 *
 * @param orderId         the order this is about
 * @param recipientUserId the customer to reach off-page; {@code null} for
 *                        staff-only messages (admin queue) or legacy orders
 *                        with no account, in which case user-targeted channels
 *                        skip the message
 * @param type            machine-readable kind (ORDER_PLACED, STATUS_CHANGED, …)
 * @param status          the order status this message reports
 * @param title           short headline for channels that show one
 * @param body            one-line human-readable detail
 * @param url             relative deep link to open when tapped
 * @param toAdmin         whether the staff queue should also receive it
 */
record NotificationMessage(
        Long orderId,
        Long recipientUserId,
        String type,
        String status,
        String title,
        String body,
        String url,
        boolean toAdmin,
        Instant at) {

    /** Customer-facing update that also refreshes the staff queue. */
    static NotificationMessage forCustomer(Long orderId, Long userId, String type, String status,
                                           String title, String body, String url) {
        return new NotificationMessage(orderId, userId, type, status, title, body, url, true, Instant.now());
    }

    /** Staff-only message (no customer recipient). */
    static NotificationMessage forAdmin(Long orderId, String type, String status, String title, String body) {
        return new NotificationMessage(orderId, null, type, status, title, body, null, true, Instant.now());
    }
}
