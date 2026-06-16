package com.townbasket.inventory;

import java.util.List;

/**
 * Published API of the inventory module. Called synchronously by the
 * {@code orders} checkout to reserve stock; reservations are later committed
 * (on delivery) or released (on cancellation), driven by order events.
 */
public interface InventoryService {

    /**
     * Atomically reserve all lines for an order at a store. Each line is a single
     * conditional UPDATE ({@code on_hand - reserved >= qty}); if any line cannot
     * be satisfied the whole reservation fails and the transaction rolls back,
     * so no partial reservation is left behind. Emits {@code StockLow} for any
     * variant whose available stock falls to/below its threshold.
     *
     * @throws com.townbasket.inventory.InsufficientStockException if a line cannot be reserved
     */
    void reserve(Long storeId, Long orderId, List<ReservationLine> lines);

    /** Commit all RESERVED reservations for an order (on delivery): on_hand -= qty. */
    void commitReservation(Long orderId);

    /** Release all RESERVED reservations for an order (on cancellation): reserved -= qty. */
    void releaseReservation(Long orderId);

    /** Currently-available units ({@code on_hand - reserved}) for a variant at any store; 0 if unknown. */
    int availability(Long variantId);
}
