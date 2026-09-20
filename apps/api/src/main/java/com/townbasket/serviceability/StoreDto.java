package com.townbasket.serviceability;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;

/**
 * Public store representation returned by the serviceability API.
 */
public record StoreDto(
        String name,
        String address,
        LocalTime openingTime,
        LocalTime closingTime,
        int deliveryRadiusMeters,
        BigDecimal minOrderValue,
        double lat,
        double lng,
        boolean open,
        boolean opensNextDay,
        /**
         * The store's public contact number, or null when staff haven't set one.
         * The storefront only offers a "call the store" route when this is
         * present — copy that tells a customer to get in touch has to have
         * somewhere to send them.
         */
        String supportPhone,
        /**
         * The store's GST registration number, or null while it isn't
         * registered. Public because a GSTIN is public by law — it has to be
         * displayed at the place of business and printed on every tax invoice —
         * so the storefront may show it, and the admin card edits it through
         * this same shape.
         */
        String gstin,
        /** True while a manual "closed for today" is in force — the reason for {@code open == false} then. */
        boolean manuallyClosed,
        /** Staff's reason for the manual closure (shown to customers), null otherwise. */
        String closedReason,
        /** When the manual closure lapses on its own, null when not manually closed. */
        Instant closedUntil) {

    /**
     * When the store next opens. Same as {@link #openingTime()}; named for the
     * closed-store copy, where it pairs with {@link #opensNextDay()} to say
     * "opens tomorrow at 8 AM" rather than just quoting the hours.
     */
    public LocalTime opensAt() {
        return openingTime;
    }
}
