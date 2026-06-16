package com.townbasket.inventory.internal;

import com.townbasket.inventory.InventoryService;
import com.townbasket.shared.events.OrderCancelled;
import com.townbasket.shared.events.OrderDelivered;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Reacts to order lifecycle events (decoupled — orders never calls inventory
 * back synchronously). {@link ApplicationModuleListener} runs the handler in its
 * own transaction after the publishing transaction commits, backed by the
 * Modulith event-publication registry (so a crash mid-handler is retried).
 *
 * <ul>
 *   <li>{@code OrderDelivered} -> commit the reservation (on_hand -= qty).</li>
 *   <li>{@code OrderCancelled} -> release the reservation (reserved -= qty).</li>
 * </ul>
 */
@Component
class InventoryOrderEventListener {

    private final InventoryService inventoryService;

    InventoryOrderEventListener(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @ApplicationModuleListener
    void on(OrderDelivered event) {
        inventoryService.commitReservation(event.orderId());
    }

    @ApplicationModuleListener
    void on(OrderCancelled event) {
        inventoryService.releaseReservation(event.orderId());
    }
}
