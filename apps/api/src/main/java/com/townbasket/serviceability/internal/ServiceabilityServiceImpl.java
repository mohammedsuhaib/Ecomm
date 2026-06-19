package com.townbasket.serviceability.internal;

import com.townbasket.serviceability.ServiceabilityCheckDto;
import com.townbasket.serviceability.ServiceabilityService;
import com.townbasket.serviceability.StoreDto;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Module-internal implementation of {@link ServiceabilityService}. Distance is
 * computed with the Haversine great-circle formula against the active store.
 *
 * <p><strong>TESTING override:</strong> when both
 * {@code townbasket.serviceability.store-lat} and {@code store-lng} are set (env
 * {@code TOWNBASKET_SERVICEABILITY_STORE_LAT} / {@code _LNG}), they replace the
 * active store's coordinates for the distance check and in {@link #activeStore()},
 * so the 5 km gate can be tested from your own location without touching the
 * seeded store. Leave unset in production; a malformed value is ignored.
 */
@Service
@Transactional(readOnly = true)
class ServiceabilityServiceImpl implements ServiceabilityService {

    private static final Logger log = LoggerFactory.getLogger(ServiceabilityServiceImpl.class);
    private static final double EARTH_RADIUS_M = 6_371_000.0;

    private final StoreRepository storeRepository;
    private final Double overrideLat;
    private final Double overrideLng;

    ServiceabilityServiceImpl(
            StoreRepository storeRepository,
            @Value("${townbasket.serviceability.store-lat:}") String overrideLatRaw,
            @Value("${townbasket.serviceability.store-lng:}") String overrideLngRaw) {
        this.storeRepository = storeRepository;
        this.overrideLat = parseCoord(overrideLatRaw);
        this.overrideLng = parseCoord(overrideLngRaw);
        if (overrideActive()) {
            log.warn("Serviceability store location OVERRIDDEN to lat={}, lng={} via "
                    + "TOWNBASKET_SERVICEABILITY_STORE_LAT/LNG — TESTING ONLY; unset for the real store.",
                    overrideLat, overrideLng);
        }
    }

    private boolean overrideActive() {
        return overrideLat != null && overrideLng != null;
    }

    private double storeLat(StoreEntity store) {
        return overrideActive() ? overrideLat : store.getLat();
    }

    private double storeLng(StoreEntity store) {
        return overrideActive() ? overrideLng : store.getLng();
    }

    /** Parse an optional coordinate override; blank/missing/malformed => no override. */
    private static Double parseCoord(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Double.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public ServiceabilityCheckDto check(double lat, double lng) {
        StoreEntity store = storeRepository.findFirstByActiveTrueOrderByIdAsc()
                .orElseThrow(() -> new IllegalStateException("No active store configured"));

        int distanceMeters = (int) Math.round(
                haversineMeters(storeLat(store), storeLng(store), lat, lng));
        boolean serviceable = distanceMeters <= store.getDeliveryRadiusM();

        return new ServiceabilityCheckDto(
                serviceable,
                distanceMeters,
                store.getDeliveryRadiusM(),
                store.getName());
    }

    @Override
    public Optional<StoreDto> activeStore() {
        return storeRepository.findFirstByActiveTrueOrderByIdAsc().map(this::toDto);
    }

    /** Great-circle distance between two lat/lng points in metres. */
    static double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_M * c;
    }

    private StoreDto toDto(StoreEntity s) {
        return new StoreDto(
                s.getName(),
                s.getAddress(),
                s.getOpeningTime(),
                s.getClosingTime(),
                s.getDeliveryRadiusM(),
                s.getMinOrderValue(),
                storeLat(s),
                storeLng(s));
    }
}
