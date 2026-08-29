package com.townbasket.notifications.internal;

/**
 * A delivery medium for notifications (ARCHITECTURE §3.8: "fans out to channels
 * behind a NotificationChannel port"). Implementations are Spring beans; the
 * dispatcher discovers them all and sends every message to each enabled one.
 *
 * <p>Contract: {@link #send} is best-effort and MUST NOT throw — a dead channel
 * can never block an order transition or another channel's delivery. Channels
 * that need configuration the deployment may not have (API keys, VAPID keys)
 * report {@link #isEnabled()} {@code false} and are skipped.
 */
interface NotificationChannel {

    /** Stable channel name, recorded in the notification log (e.g. {@code "SSE"}). */
    String name();

    /** {@code false} when the channel lacks the config it needs to deliver. */
    boolean isEnabled();

    /**
     * Deliver the message. Returns {@code true} when something was actually
     * sent (so the log records a real delivery rather than a no-op skip, e.g.
     * a push to a customer with no subscriptions).
     */
    boolean send(NotificationMessage message);
}
