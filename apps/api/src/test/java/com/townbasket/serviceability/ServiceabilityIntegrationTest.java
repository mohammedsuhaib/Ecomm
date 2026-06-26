package com.townbasket.serviceability;

import static org.assertj.core.api.Assertions.assertThat;

import com.townbasket.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Serviceability integration test against a real Postgres (Testcontainers).
 * Verifies the seeded store loads and the Haversine radius check behaves at the
 * 5 km boundary through the public {@link ServiceabilityService}.
 */
class ServiceabilityIntegrationTest extends AbstractIntegrationTest {

    // Seeded store coordinates (approximate; see V3 serviceability seed).
    private static final double STORE_LAT = 12.21;
    private static final double STORE_LNG = 76.89;

    @Autowired
    ServiceabilityService serviceabilityService;

    @Test
    void seededStoreIsExposed() {
        StoreDto store = serviceabilityService.activeStore().orElseThrow();

        assertThat(store.name()).isEqualTo("Town Basket");
        assertThat(store.deliveryRadiusMeters()).isEqualTo(5000);
        assertThat(store.minOrderValue()).isEqualByComparingTo("299");
        assertThat(store.openingTime().toString()).isEqualTo("08:00");
        assertThat(store.closingTime().toString()).isEqualTo("21:00");
    }

    @Test
    void pointWithinFiveKmIsServiceable() {
        // ~1 km north of the store (0.009 deg latitude ~= 1 km).
        ServiceabilityCheckDto result = serviceabilityService.check(STORE_LAT + 0.009, STORE_LNG);

        assertThat(result.serviceable()).isTrue();
        assertThat(result.radiusMeters()).isEqualTo(5000);
        assertThat(result.distanceMeters()).isLessThan(5000);
        assertThat(result.storeName()).isEqualTo("Town Basket");
    }

    @Test
    void farPointIsNotServiceable() {
        // Mysuru city centre is ~25 km away — well outside the 5 km radius.
        ServiceabilityCheckDto result = serviceabilityService.check(12.2958, 76.6394);

        assertThat(result.serviceable()).isFalse();
        assertThat(result.distanceMeters()).isGreaterThan(5000);
    }

    @Test
    void exactStoreLocationIsZeroDistanceAndServiceable() {
        ServiceabilityCheckDto result = serviceabilityService.check(STORE_LAT, STORE_LNG);

        assertThat(result.distanceMeters()).isEqualTo(0);
        assertThat(result.serviceable()).isTrue();
    }
}
