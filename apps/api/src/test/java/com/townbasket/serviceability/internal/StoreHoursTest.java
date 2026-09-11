package com.townbasket.serviceability.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import org.junit.jupiter.api.Test;

/**
 * Store opening hours — the single rule behind both the storefront's closed
 * banner and the checkout rejection. They read the same flag, so if this is
 * wrong the shop either turns away orders it could serve or accepts orders
 * nobody can fulfil.
 */
class StoreHoursTest {

    private static final LocalTime OPEN = LocalTime.of(8, 0);
    private static final LocalTime CLOSE = LocalTime.of(21, 0);

    @Test
    void openThroughTheTradingDayAndClosedOutsideIt() {
        assertThat(ServiceabilityServiceImpl.isOpenAt(LocalTime.of(12, 0), OPEN, CLOSE)).isTrue();
        assertThat(ServiceabilityServiceImpl.isOpenAt(LocalTime.of(7, 59), OPEN, CLOSE)).isFalse();
        assertThat(ServiceabilityServiceImpl.isOpenAt(LocalTime.of(21, 1), OPEN, CLOSE)).isFalse();
        assertThat(ServiceabilityServiceImpl.isOpenAt(LocalTime.of(3, 0), OPEN, CLOSE)).isFalse();
    }

    @Test
    void theBoundaryMinutesCountAsOpen() {
        // A customer ordering exactly at opening or closing time must not be
        // turned away by an off-by-one.
        assertThat(ServiceabilityServiceImpl.isOpenAt(OPEN, OPEN, CLOSE)).isTrue();
        assertThat(ServiceabilityServiceImpl.isOpenAt(CLOSE, OPEN, CLOSE)).isTrue();
    }

    @Test
    void beforeOpeningItOpensLaterTodayAfterClosingItIsTomorrow() {
        // This is what decides "opens today at 8 AM" vs "opens tomorrow at 8 AM".
        assertThat(ServiceabilityServiceImpl.opensNextDay(LocalTime.of(6, 30), OPEN, CLOSE)).isFalse();
        assertThat(ServiceabilityServiceImpl.opensNextDay(LocalTime.of(22, 30), OPEN, CLOSE)).isTrue();
        assertThat(ServiceabilityServiceImpl.opensNextDay(LocalTime.of(0, 30), OPEN, CLOSE)).isFalse();
    }

    @Test
    void anOvernightWindowStaysOpenAcrossMidnight() {
        // A late-night store (18:00 -> 02:00) must not read as closed at 01:00.
        LocalTime open = LocalTime.of(18, 0);
        LocalTime close = LocalTime.of(2, 0);

        assertThat(ServiceabilityServiceImpl.isOpenAt(LocalTime.of(23, 0), open, close)).isTrue();
        assertThat(ServiceabilityServiceImpl.isOpenAt(LocalTime.of(1, 0), open, close)).isTrue();
        assertThat(ServiceabilityServiceImpl.isOpenAt(LocalTime.of(12, 0), open, close)).isFalse();
        // Closed at midday, it reopens the same evening — not "tomorrow".
        assertThat(ServiceabilityServiceImpl.opensNextDay(LocalTime.of(12, 0), open, close)).isFalse();
    }
}
