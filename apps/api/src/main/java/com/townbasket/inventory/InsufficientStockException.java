package com.townbasket.inventory;

import com.townbasket.shared.BusinessRuleException;

/**
 * Thrown by {@link InventoryService#reserve} when a line cannot be reserved
 * because not enough stock is available. The global handler renders it as 422
 * (business-rule violation).
 */
public class InsufficientStockException extends BusinessRuleException {

    public InsufficientStockException(Long variantId, int requested, int available) {
        super("Insufficient stock for variant " + variantId
                + ": requested " + requested + ", available " + available);
    }
}
