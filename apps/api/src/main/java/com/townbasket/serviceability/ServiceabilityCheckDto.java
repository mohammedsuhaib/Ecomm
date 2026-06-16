package com.townbasket.serviceability;

/**
 * Result of a serviceability (delivery-radius) check against the active store.
 */
public record ServiceabilityCheckDto(
        boolean serviceable,
        int distanceMeters,
        int radiusMeters,
        String storeName) {
}
