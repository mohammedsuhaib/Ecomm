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
