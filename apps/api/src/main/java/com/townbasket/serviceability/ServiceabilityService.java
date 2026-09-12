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

    /** Admin: replace the store's operating settings (hours, radius, minimum, location). */
    StoreDto updateStore(StoreUpdateRequest request);

    /**
     * Admin: close the store for the rest of TODAY (store-local) with an optional
     * reason. Expires by itself at midnight, so a forgotten reopen costs at most
     * the day that was intended. Reopen early with {@link #reopen()}.
     */
    StoreDto closeForToday(String reason);

    /** Admin: lift a manual closure; trading hours apply again immediately. */
    StoreDto reopen();
}
