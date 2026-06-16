package com.townbasket.serviceability;

import java.math.BigDecimal;
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
        double lng) {
}
