package com.townbasket.catalog.internal;

import com.townbasket.shared.events.OrderDelivered;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Counts delivered units per product for the "popular" ordering. Runs after the
 * delivering transaction commits, backed by the event publication registry, so a
 * crash mid-handler is retried rather than losing the sale.
 */
@Component
class CatalogOrderEventListener {

    private final ProductRepository products;

    CatalogOrderEventListener(ProductRepository products) {
        this.products = products;
    }

    @ApplicationModuleListener
    void on(OrderDelivered event) {
        if (event.lines() == null) {
            return; // serialised before lines existed; V6_15 counted it
        }
        for (OrderDelivered.Line line : event.lines()) {
            products.addSold(line.variantId(), line.qty());
        }
    }
}
