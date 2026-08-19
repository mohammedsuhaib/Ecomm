package com.townbasket.inventory.internal;

import com.townbasket.inventory.InventoryService;
import com.townbasket.shared.events.OrderCancelled;
import com.townbasket.shared.events.OrderDelivered;
import com.townbasket.shared.events.VariantCreated;
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

    // Single-MVP-store id, same literal the seeds use (see V4_2's rationale).
    private static final long MVP_STORE_ID = 1L;

    private final InventoryService inventoryService;
    private final StockLevelRepository stockLevels;

    InventoryOrderEventListener(InventoryService inventoryService, StockLevelRepository stockLevels) {
        this.inventoryService = inventoryService;
        this.stockLevels = stockLevels;
    }

    /**
     * A new catalog variant gets a zero-stock row so it shows up in the admin
     * stock list (and as out-of-stock on the storefront) instead of being
     * invisible until a manual correction. Idempotent: the (store, variant)
     * unique key backs the exists-check.
     */
    @ApplicationModuleListener
    void on(VariantCreated event) {
        if (stockLevels.findByStoreIdAndVariantId(MVP_STORE_ID, event.variantId()).isEmpty()) {
            stockLevels.save(StockLevelEntity.zeroRow(MVP_STORE_ID, event.variantId()));
        }
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
