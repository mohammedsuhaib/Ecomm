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
 */
@Component
class StoreDetails {

    private final String name;
    private final String address;
    private final String contact;

    /** Store GSTIN; blank when unset (hidden on the header in that case). */
    private final String gstin;

    StoreDetails(
            @Value("${townbasket.invoice.store-name:Town Basket}") String name,
            @Value("${townbasket.invoice.store-address:Mysuru, Karnataka, India}") String address,
            @Value("${townbasket.invoice.store-contact:town-basket.com}") String contact,
            @Value("${townbasket.invoice.gstin:}") String gstin) {
        this.name = name;
        this.address = address;
        this.contact = contact;
        this.gstin = gstin == null ? "" : gstin.trim();
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

    String gstin() {
        return gstin;
    }
}
