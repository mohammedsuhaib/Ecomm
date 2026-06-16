package com.townbasket.serviceability;

import java.util.Optional;

/**
 * Published API of the serviceability module.
 */
public interface ServiceabilityService {

    /**
     * Check whether a customer location is within the active store's delivery
     * radius, using the Haversine great-circle distance.
     */
    ServiceabilityCheckDto check(double lat, double lng);

    /** The active store's public details, if a store is configured. */
    Optional<StoreDto> activeStore();
}
