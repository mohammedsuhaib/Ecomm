/**
 * {@code notifications} module — consumes order/payment events and fans out to
 * channels behind a {@code NotificationChannel} port.
 *
 * <p>Core channel is in-app SSE (live order tracking + admin queue alerts).
 * Web Push / Email / SMS / WhatsApp are additive add-on implementations.
 * Delivery is best-effort and never blocks an order transition.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Notifications")
package com.townbasket.notifications;
