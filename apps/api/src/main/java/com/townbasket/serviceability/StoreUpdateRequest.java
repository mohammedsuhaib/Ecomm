package com.townbasket.serviceability;

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * Admin edit of the store's operating settings. Every field is required — the
 * form always submits the whole card, so a partial update has nothing to mean.
 * {@code active} and the id are deliberately not editable here.
 */
public record StoreUpdateRequest(
        String name,
        String address,
        double lat,
        double lng,
        int deliveryRadiusMeters,
        LocalTime openingTime,
        LocalTime closingTime,
        BigDecimal minOrderValue) {
}
