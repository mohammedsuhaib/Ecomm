package com.townbasket.orders.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Store identity shown on order-facing documents (the GST invoice and the
 * admin sales report), sourced from the {@code townbasket.invoice.*}
 * properties — the one place these are configured. {@link InvoicePdfGenerator}
 * reads the same properties directly on its own constructor; this holder
 * exists so the report generators (which need the identical values) don't
 * each repeat the {@code @Value} defaults a third and fourth time.
 *
 * <p>Deliberately carries no GSTIN: that used to be a static
 * {@code townbasket.invoice.gstin} property, but it is now the store's own
 * setting, snapshotted onto each order as its invoice is issued (see
 * {@code application.yml}'s note on {@code invoice_gstin}). A report spanning
 * many orders shows the store's <em>current</em> GSTIN, read fresh from
 * {@code ServiceabilityService} by the caller, not a value cached here.
 */
@Component
class StoreDetails {

    private final String name;
    private final String address;
    private final String contact;

    StoreDetails(
            @Value("${townbasket.invoice.store-name:Town Basket}") String name,
            @Value("${townbasket.invoice.store-address:Mysuru, Karnataka, India}") String address,
            @Value("${townbasket.invoice.store-contact:town-basket.com}") String contact) {
        this.name = name;
        this.address = address;
        this.contact = contact;
    }

    String name() {
        return name;
    }

    String address() {
        return address;
    }

    String contact() {
        return contact;
    }
}
