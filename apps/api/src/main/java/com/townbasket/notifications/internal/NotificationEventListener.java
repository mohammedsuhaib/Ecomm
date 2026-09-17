package com.townbasket.notifications.internal;

import com.townbasket.shared.events.OrderAssigned;
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

    /** Packed, bagged, and waiting on the counter for a rider to collect. */
    private static final String READY_FOR_DELIVERY = "READY_FOR_DELIVERY";

    /** In the rider's hands and on the road. */
    private static final String OUT_FOR_DELIVERY = "OUT_FOR_DELIVERY";

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
                "New order #" + label(event.publicCode(), event.orderId()),
                "A new order just came in."));
    }

    @ApplicationModuleListener
    void on(OrderConfirmed event) {
        // Staff confirmed it from the queue. The customer is told through the
        // OrderStatusChanged published by the same transition; this copy keeps
        // the other dashboards' live streams in step (see the class note on
        // double-buzzing).
        dispatch(NotificationMessage.forAdmin(
                event.orderId(), "ORDER_CONFIRMED", "CONFIRMED",
                "Order #" + label(event.publicCode(), event.orderId()) + " confirmed",
                "Accepted by staff and ready to pack."));
    }

    @ApplicationModuleListener
    void on(OrderStatusChanged event) {
        String status = event.toStatus();
        dispatch(NotificationMessage.forCustomer(
                event.orderId(),
                event.userId(),
                "STATUS_CHANGED",
                status,
                titleFor(status, label(event.publicCode(), event.orderId())),
                bodyFor(status),
                event.trackingToken() == null ? null : "/order/" + event.trackingToken()));

        if ("DELIVERY_FAILED".equals(status)) {
            // Staff must act on this one — re-dispatch or cancel — so it goes to
            // the dashboard like a new order does, not just to the customer.
            dispatch(NotificationMessage.forAdmin(
                    event.orderId(), "DELIVERY_FAILED", status,
                    "Order #" + label(event.publicCode(), event.orderId()) + " could not be delivered",
                    "The rider reported a failed attempt. Re-dispatch or cancel it from the queue."));
        }

        if (event.assignedAgentId() == null) {
            return;
        }
        // The rider's own copy of the two transitions that change what they do.
        //
        // READY_FOR_DELIVERY, not OUT_FOR_DELIVERY: being ready is the moment a
        // job becomes the rider's to start — there is a bag on the counter with
        // their name on it. OUT_FOR_DELIVERY is now their OWN action (they tap
        // "Picked up"), so buzzing them for it would be the app telling them
        // what they just did.
        if (READY_FOR_DELIVERY.equals(status)) {
            dispatch(NotificationMessage.forAgent(
                    event.orderId(), event.assignedAgentId(), "ORDER_ASSIGNED", status,
                    "Order ready to collect",
                    destination(event.addressLine())));
        } else if ("CANCELLED".equals(status)) {
            // Otherwise they drive to a delivery that is no longer happening.
            dispatch(NotificationMessage.forAgent(
                    event.orderId(), event.assignedAgentId(), "ORDER_CANCELLED", status,
                    "Delivery cancelled",
                    "Order #" + label(event.publicCode(), event.orderId()) + " was cancelled — no need to deliver it."));
        }
    }

    /**
     * A rider gained or lost a job.
     *
     * <p>The new rider is only buzzed when the order is one they can act on
     * right now: waiting on the counter for them, or already in their hands.
     * Orders are normally assigned while still being packed, and a rider's
     * working list is those two statuses, so notifying at assignment time would
     * point them at a job they cannot see or start yet; the READY_FOR_DELIVERY
     * transition above notifies them at the moment it becomes real.
     *
     * <p>Losing a job is always worth telling them, whatever the status — a
     * rider must not set off for a delivery that is no longer theirs.
     */
    @ApplicationModuleListener
    void on(OrderAssigned event) {
        boolean actionable = READY_FOR_DELIVERY.equals(event.status())
                || OUT_FOR_DELIVERY.equals(event.status());
        if (event.agentId() != null && actionable) {
            dispatch(NotificationMessage.forAgent(
                    event.orderId(), event.agentId(), "ORDER_ASSIGNED", event.status(),
                    "New delivery assigned",
                    destination(event.addressLine())));
        }
        if (event.previousAgentId() != null) {
            dispatch(NotificationMessage.forAgent(
                    event.orderId(), event.previousAgentId(), "ORDER_UNASSIGNED", event.status(),
                    "Delivery reassigned",
                    "Order #" + label(event.publicCode(), event.orderId()) + " is no longer assigned to you."));
        }
    }

    @ApplicationModuleListener
    void on(OrderDelivered event) {
        dispatch(NotificationMessage.forAdmin(
                event.orderId(), "ORDER_DELIVERED", "DELIVERED",
                "Order #" + label(event.publicCode(), event.orderId()) + " delivered",
                "Handover confirmed."));
    }

    @ApplicationModuleListener
    void on(OrderCancelled event) {
        dispatch(NotificationMessage.forAdmin(
                event.orderId(), "ORDER_CANCELLED", "CANCELLED",
                "Order #" + label(event.publicCode(), event.orderId()) + " cancelled",
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

    /** Where the rider is going, or a nudge to open the app when unknown. */
    private static String destination(String addressLine) {
        return addressLine == null || addressLine.isBlank()
                ? "Open the app for the delivery details."
                : "Deliver to " + addressLine;
    }

    /**
     * The order's public name for notification text: the short customer-facing
     * code, falling back to the numeric id only for an event that was
     * serialised into the outbox before the code existed and is being
     * republished on restart. Customer-facing text must never show the id on
     * its own — being sequential, it publishes the store's order volume.
     */
    private static String label(String publicCode, Long orderId) {
        return publicCode == null || publicCode.isBlank() ? String.valueOf(orderId) : publicCode;
    }

    private static String titleFor(String status, String orderLabel) {
        return switch (status) {
            case "CONFIRMED" -> "Order confirmed";
            case "PACKING" -> "We're packing your order";
            case "READY_FOR_DELIVERY" -> "Your order is packed";
            case "OUT_FOR_DELIVERY" -> "Your order is on the way";
            case "DELIVERY_FAILED" -> "We couldn't deliver your order";
            case "DELIVERED" -> "Order delivered";
            case "CANCELLED" -> "Order cancelled";
            default -> "Order #" + orderLabel + " updated";
        };
    }

    /**
     * One line on a lock screen, so each says the single thing the customer
     * would want to know at that moment — what the shop is doing, or what they
     * need to do next.
     *
     * <p>Written for a grocery shop, deliberately. "Preparing your order" reads
     * like a kitchen cooking something; nobody here is cooking, they are taking
     * packets off shelves and putting them in a bag. The words the staff and the
     * app already use — accepted, packed, on its way — are the accurate ones,
     * and they match the status the customer sees on the tracking page.
     *
     * <p>No stock or fulfilment vocabulary either: "reserved items released" is
     * a warehouse fact about our inventory table, not news about their shopping.
     */
    private static String bodyFor(String status) {
        return switch (status) {
            case "CONFIRMED" -> "The store has accepted your order and will start packing it shortly.";
            case "PACKING" -> "Your items are being picked off the shelves and packed.";
            case "READY_FOR_DELIVERY" -> "Your bag is ready at the store and waiting for a delivery person to collect it.";
            case "OUT_FOR_DELIVERY" -> "Your delivery code is in the app — have it ready for the delivery person.";
            case "DELIVERY_FAILED" -> "Our rider couldn't reach you. The store will call you to arrange another attempt.";
            case "DELIVERED" -> "Your groceries have been handed over. Thank you for shopping with Town Basket!";
            case "CANCELLED" -> "Your order was cancelled. Tap to see the details.";
            default -> "Tap to see the latest status of your order.";
        };
    }
}
