package com.townbasket.inventory.internal;

import java.math.BigDecimal;

/**
 * One row of the inventory valuation export: on-hand quantity valued at cost
 * price. Deliberately module-internal and separate from the public
 * {@link com.townbasket.inventory.StockLevelDto} — cost price is admin/export
 * data only, never customer-facing, mirroring how {@code catalog.AdminVariantDto}
 * exposes {@code costPrice} only on its own admin-facing DTO.
 */
record InventoryValuationRow(
        String productName,
        String variantLabel,
        int onHand,
        BigDecimal costPrice) {

    /** Row total: on-hand quantity (physically on the shelf) × unit cost price. */
    BigDecimal totalValue() {
        return costPrice.multiply(BigDecimal.valueOf(onHand));
    }
}
