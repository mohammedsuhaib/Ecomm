package com.townbasket.serviceability.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;

/**
 * An immutable copy of the active store row — the value {@link ActiveStoreCache}
 * holds.
 *
 * <p>Three reasons this exists instead of caching something that already did:
 * <ul>
 *   <li>Not the {@link StoreEntity}: a cached entity outlives the persistence
 *       context that loaded it, so it would be handed to callers detached and
 *       shared, and the admin write paths mutate the entity they load.</li>
 *   <li>Not the {@code StoreDto}: that DTO carries {@code open} /
 *       {@code opensNextDay} / {@code manuallyClosed}, which are derived from the
 *       clock. Caching it would freeze "are we open" for the cache's lifetime and
 *       let the storefront banner disagree with what checkout does.</li>
 *   <li>A record, so nothing downstream can mutate the shared instance.</li>
 * </ul>
 * {@code active} is not a field: the only way to obtain a snapshot is a lookup
 * that already filtered on it.
 */
record StoreSnapshot(
        Long id,
        String name,
        String address,
        double lat,
        double lng,
        int deliveryRadiusM,
        LocalTime openingTime,
        LocalTime closingTime,
        BigDecimal minOrderValue,
        String supportPhone,
        String gstin,
        Instant closedUntil,
        String closedReason) {

    static StoreSnapshot of(StoreEntity e) {
        return new StoreSnapshot(
                e.getId(),
                e.getName(),
                e.getAddress(),
                e.getLat(),
                e.getLng(),
                e.getDeliveryRadiusM(),
                e.getOpeningTime(),
                e.getClosingTime(),
                e.getMinOrderValue(),
                e.getSupportPhone(),
                e.getGstin(),
                e.getClosedUntil(),
                e.getClosedReason());
    }
}
