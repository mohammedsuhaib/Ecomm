package com.townbasket.notifications.internal;

import com.townbasket.shared.events.OrderCancelled;
import com.townbasket.shared.events.OrderConfirmed;
import com.townbasket.shared.events.OrderDelivered;
import com.townbasket.shared.events.OrderPlaced;
import com.townbasket.shared.events.OrderStatusChanged;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Consumes order domain events, turns them into channel-neutral
 * {@link NotificationMessage}s, and fans them out to every enabled
 * {@link NotificationChannel}, logging each delivery.
 *
 * <p>Best-effort by construction: the publishing transaction has already
 * committed before these handlers run, and a channel that throws is caught and
 * logged so it can never block an order transition or starve another channel.
 *
 * <p><strong>One notification per transition.</strong> {@code transition()}
 * publishes {@code OrderStatusChanged} <em>and</em> a specific
 * {@code OrderDelivered}/{@code OrderCancelled} for the same change, so only
 * {@code OrderStatusChanged} is treated as customer-notifiable; the specific
 * events would otherwise buzz the customer's phone twice for one event.
 */
@Component
class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final List<NotificationChannel> channels;
    private final NotificationLogRepository logRepository;

    NotificationEventListener(List<NotificationChannel> channels, NotificationLogRepository logRepository) {
        this.channels = channels;
        this.logRepository = logRepository;
    }

    @ApplicationModuleListener
    void on(OrderPlaced event) {
        dispatch(NotificationMessage.forAdmin(
                event.orderId(), "ORDER_PLACED", "PLACED",
                "New order #" + event.orderId(),
                "A new order just came in."));
    }

    @ApplicationModuleListener
    void on(OrderConfirmed event) {
        // Checkout-time confirmation: the customer is still on the page, so this
        // only refreshes the live streams (see the class note on double-buzzing).
        dispatch(NotificationMessage.forAdmin(
                event.orderId(), "ORDER_CONFIRMED", "CONFIRMED",
                "Order #" + event.orderId() + " confirmed",
                "Payment accepted."));
    }

    @ApplicationModuleListener
    void on(OrderStatusChanged event) {
        String status = event.toStatus();
        dispatch(NotificationMessage.forCustomer(
                event.orderId(),
                event.userId(),
                "STATUS_CHANGED",
                status,
                titleFor(status, event.orderId()),
                bodyFor(status),
                event.trackingToken() == null ? null : "/order/" + event.trackingToken()));
    }

    @ApplicationModuleListener
    void on(OrderDelivered event) {
        dispatch(NotificationMessage.forAdmin(
                event.orderId(), "ORDER_DELIVERED", "DELIVERED",
                "Order #" + event.orderId() + " delivered",
                "Handover confirmed."));
    }

    @ApplicationModuleListener
    void on(OrderCancelled event) {
        dispatch(NotificationMessage.forAdmin(
                event.orderId(), "ORDER_CANCELLED", "CANCELLED",
                "Order #" + event.orderId() + " cancelled",
                "Reserved items have been released."));
    }

    /** Send to every enabled channel; log what actually went out. */
    private void dispatch(NotificationMessage message) {
        for (NotificationChannel channel : channels) {
            if (!channel.isEnabled()) {
                continue;
            }
            try {
                if (channel.send(message)) {
                    logRepository.save(new NotificationLogEntity(
                            message.orderId(), channel.name(), message.type()));
                }
            } catch (RuntimeException e) {
                // Never let one channel's failure block the others or the order.
                log.warn("Notification channel {} failed for order {} ({}): {}",
                        channel.name(), message.orderId(), message.type(), e.toString());
            }
        }
    }

    private static String titleFor(String status, Long orderId) {
        return switch (status) {
            case "CONFIRMED" -> "Order confirmed";
            case "PACKING" -> "We're packing your order";
            case "OUT_FOR_DELIVERY" -> "Your order is on the way";
            case "DELIVERED" -> "Order delivered";
            case "CANCELLED" -> "Order cancelled";
            default -> "Order #" + orderId + " updated";
        };
    }

    private static String bodyFor(String status) {
        return switch (status) {
            case "CONFIRMED" -> "We've received your order and will start preparing it shortly.";
            case "PACKING" -> "Your groceries are being packed for delivery.";
            case "OUT_FOR_DELIVERY" -> "Keep your delivery code handy to hand over to the delivery person.";
            case "DELIVERED" -> "Thank you for shopping with Town Basket!";
            case "CANCELLED" -> "Your order was cancelled and any reserved items released.";
            default -> "Tap to see the latest status of your order.";
        };
    }
}
