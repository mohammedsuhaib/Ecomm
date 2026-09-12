package com.townbasket.serviceability.internal;

import com.townbasket.serviceability.ServiceabilityCheckDto;
import com.townbasket.serviceability.ServiceabilityService;
import com.townbasket.serviceability.StoreDto;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import com.townbasket.serviceability.StoreUpdateRequest;
import java.time.LocalTime;
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
    private final Clock clock;
    private final Double overrideLat;
    private final Double overrideLng;

    ServiceabilityServiceImpl(
            StoreRepository storeRepository,
            Clock clock,
            @Value("${townbasket.serviceability.store-lat:}") String overrideLatRaw,
            @Value("${townbasket.serviceability.store-lng:}") String overrideLngRaw) {
        this.storeRepository = storeRepository;
        this.clock = clock;
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

    /**
     * Whether the store is serving right now, on the SERVER's clock — never the
     * customer's device, whose timezone may be anything. Supports an overnight
     * window (closing before opening), though the MVP store opens 08:00-21:00.
     *
     * <p>This is the single source of truth for "are we open": {@code orders}
     * rejects checkout using this same flag, so the storefront banner can never
     * disagree with what happens at checkout.
     */
    static boolean isOpenAt(LocalTime now, LocalTime opening, LocalTime closing) {
        return !opening.isAfter(closing)
                ? !now.isBefore(opening) && !now.isAfter(closing)   // same-day window
                : !now.isBefore(opening) || !now.isAfter(closing);  // crosses midnight
    }

    /**
     * True when the next opening falls on the following day — i.e. today's
     * trading window has already closed. False when the store simply has not
     * opened yet today (or the window runs overnight, so it reopens later today).
     */
    static boolean opensNextDay(LocalTime now, LocalTime opening, LocalTime closing) {
        if (opening.isAfter(closing)) {
            return false; // overnight window: it reopens later the same day
        }
        return now.isAfter(closing);
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

    @Override
    @Transactional
    public StoreDto updateStore(StoreUpdateRequest r) {
        StoreEntity store = requireActiveStore();
        if (r == null) {
            throw new IllegalArgumentException("request body is required");
        }
        if (isBlank(r.name()) || isBlank(r.address())) {
            throw new IllegalArgumentException("name and address are required");
        }
        if (r.openingTime() == null || r.closingTime() == null || r.openingTime().equals(r.closingTime())) {
            throw new IllegalArgumentException("opening and closing time are required and must differ");
        }
        if (r.deliveryRadiusMeters() < 500 || r.deliveryRadiusMeters() > 50_000) {
            throw new IllegalArgumentException("deliveryRadiusMeters must be between 500 and 50000");
        }
        if (r.minOrderValue() == null || r.minOrderValue().signum() < 0) {
            throw new IllegalArgumentException("minOrderValue must be >= 0");
        }
        if (r.lat() < -90 || r.lat() > 90 || r.lng() < -180 || r.lng() > 180) {
            throw new IllegalArgumentException("lat/lng out of range");
        }
        store.updateSettings(r.name().trim(), r.address().trim(), r.lat(), r.lng(),
                r.deliveryRadiusMeters(), r.openingTime(), r.closingTime(), r.minOrderValue());
        return toDto(storeRepository.saveAndFlush(store));
    }

    @Override
    @Transactional
    public StoreDto closeForToday(String reason) {
        StoreEntity store = requireActiveStore();
        // End of today in the STORE's zone (the Clock bean is Asia/Kolkata), so
        // "today" means the shop's day, never the server's or the admin's laptop.
        Instant until = LocalDate.now(clock).atTime(LocalTime.MAX).atZone(clock.getZone()).toInstant();
        store.closeUntil(until, isBlank(reason) ? null : reason.trim());
        log.info("Store {} closed manually until {} ({})", store.getId(), until, reason);
        return toDto(storeRepository.saveAndFlush(store));
    }

    @Override
    @Transactional
    public StoreDto reopen() {
        StoreEntity store = requireActiveStore();
        store.reopen();
        log.info("Store {} manual closure lifted", store.getId());
        return toDto(storeRepository.saveAndFlush(store));
    }

    private StoreEntity requireActiveStore() {
        return storeRepository.findFirstByActiveTrueOrderByIdAsc()
                .orElseThrow(() -> new IllegalStateException("No active store configured"));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * Whether a manual closure is in force: closed_until set and still ahead of
     * now. A lapsed closure is simply ignored — no cleanup job needed.
     */
    static boolean manuallyClosedAt(Instant now, Instant closedUntil) {
        return closedUntil != null && now.isBefore(closedUntil);
    }

    /**
     * With a manual closure in force, does the store next open on a later day?
     * True when the closure runs to (or past) today's closing time — there is no
     * trading left today — and also when today's window has already closed
     * anyway. False when the closure lifts before closing time, since the shop
     * can still open later today. {@code closureEnd} is in store-local time.
     */
    static boolean manualClosureOpensNextDay(LocalDateTime nowLocal, LocalDateTime closureEnd, LocalTime closing) {
        if (!closureEnd.toLocalDate().equals(nowLocal.toLocalDate())) {
            return true; // runs into tomorrow or beyond
        }
        return !closureEnd.toLocalTime().isBefore(closing) || nowLocal.toLocalTime().isAfter(closing);
    }

    private StoreDto toDto(StoreEntity s) {
        Instant nowInstant = clock.instant();
        LocalTime now = LocalTime.now(clock);
        boolean manuallyClosed = manuallyClosedAt(nowInstant, s.getClosedUntil());
        boolean open = !manuallyClosed && isOpenAt(now, s.getOpeningTime(), s.getClosingTime());
        boolean nextDay = manuallyClosed
                ? manualClosureOpensNextDay(
                        LocalDateTime.ofInstant(nowInstant, clock.getZone()),
                        LocalDateTime.ofInstant(s.getClosedUntil(), clock.getZone()),
                        s.getClosingTime())
                : opensNextDay(now, s.getOpeningTime(), s.getClosingTime());
        return new StoreDto(
                s.getName(),
                s.getAddress(),
                s.getOpeningTime(),
                s.getClosingTime(),
                s.getDeliveryRadiusM(),
                s.getMinOrderValue(),
                storeLat(s),
                storeLng(s),
                open,
                nextDay,
                manuallyClosed,
                manuallyClosed ? s.getClosedReason() : null,
                manuallyClosed ? s.getClosedUntil() : null);
    }
}
