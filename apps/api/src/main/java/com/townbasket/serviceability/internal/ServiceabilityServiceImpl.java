package com.townbasket.serviceability.internal;

import com.townbasket.serviceability.ServiceabilityCheckDto;
import com.townbasket.serviceability.ServiceabilityService;
import com.townbasket.serviceability.StoreDto;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Module-internal implementation of {@link ServiceabilityService}. Distance is
 * computed with the Haversine great-circle formula against the active store.
 */
@Service
@Transactional(readOnly = true)
class ServiceabilityServiceImpl implements ServiceabilityService {

    private static final double EARTH_RADIUS_M = 6_371_000.0;

    private final StoreRepository storeRepository;

    ServiceabilityServiceImpl(StoreRepository storeRepository) {
        this.storeRepository = storeRepository;
    }

    @Override
    public ServiceabilityCheckDto check(double lat, double lng) {
        StoreEntity store = storeRepository.findFirstByActiveTrueOrderByIdAsc()
                .orElseThrow(() -> new IllegalStateException("No active store configured"));

        int distanceMeters = (int) Math.round(
                haversineMeters(store.getLat(), store.getLng(), lat, lng));
        boolean serviceable = distanceMeters <= store.getDeliveryRadiusM();

        return new ServiceabilityCheckDto(
                serviceable,
                distanceMeters,
                store.getDeliveryRadiusM(),
                store.getName());
    }

    @Override
    public Optional<StoreDto> activeStore() {
        return storeRepository.findFirstByActiveTrueOrderByIdAsc().map(ServiceabilityServiceImpl::toDto);
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

    private static StoreDto toDto(StoreEntity s) {
        return new StoreDto(
                s.getName(),
                s.getAddress(),
                s.getOpeningTime(),
                s.getClosingTime(),
                s.getDeliveryRadiusM(),
                s.getMinOrderValue(),
                s.getLat(),
                s.getLng());
    }
}
