package com.townbasket.inventory;

import com.townbasket.shared.PagedResponse;

/**
 * Published admin API of the inventory module: stock-level listing and physical
 * count corrections. Separated from {@link InventoryService} so the system-facing
 * operations (reserve/commit/release) stay decoupled from staff-facing ones.
 */
public interface AdminInventoryService {

    /**
     * Paged list of all stock levels for a store, joined with product and variant
     * names from the catalog schema for display in the admin panel.
     */
    PagedResponse<StockLevelDto> listStockLevels(Long storeId, int page, int size);

    /**
     * Physical-count correction: sets {@code on_hand} to {@code newOnHand} for
     * the given variant, records a stock movement, and leaves {@code reserved}
     * unchanged (outstanding reservations stay valid).
     *
     * @throws com.townbasket.shared.ResourceNotFoundException if no stock row exists for this variant/store
     * @throws com.townbasket.shared.BusinessRuleException    if {@code newOnHand < 0}
     */
    void correctStock(Long storeId, Long variantId, int newOnHand, String reason);
}
