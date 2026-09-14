package com.townbasket.serviceability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.townbasket.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Admin store settings against a real Postgres: the edit round-trips, the
 * "closed for today" switch flips the SAME {@code open} flag that the banner
 * and checkout read, and reopen lifts it. Restores the seeded values after
 * each test because the container is shared with every other test class.
 */
class StoreSettingsIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    ServiceabilityService serviceabilityService;

    private StoreDto original;

    private StoreDto snapshot() {
        if (original == null) {
            original = serviceabilityService.activeStore().orElseThrow();
        }
        return original;
    }

    @AfterEach
    void restore() {
        if (original == null) {
            return;
        }
        serviceabilityService.reopen();
        serviceabilityService.updateStore(new StoreUpdateRequest(
                original.name(), original.address(), original.lat(), original.lng(),
                original.deliveryRadiusMeters(), original.openingTime(), original.closingTime(),
                original.minOrderValue(), original.supportPhone()));
    }

    @Test
    void settingsRoundTripAndTheNewHoursApplyImmediately() {
        StoreDto before = snapshot();

        // Open 24h so the assertion cannot depend on the wall clock.
        StoreDto updated = serviceabilityService.updateStore(new StoreUpdateRequest(
                "Town Basket QA", before.address(), before.lat(), before.lng(),
                7_000, LocalTime.of(0, 0), LocalTime.of(23, 59), new BigDecimal("199.00"),
                "08212345678"));

        assertThat(updated.name()).isEqualTo("Town Basket QA");
        assertThat(updated.supportPhone()).isEqualTo("08212345678");
        assertThat(updated.deliveryRadiusMeters()).isEqualTo(7_000);
        assertThat(updated.minOrderValue()).isEqualByComparingTo("199.00");
        assertThat(updated.open()).as("00:00-23:59 is always open").isTrue();
        // What the public /store endpoint serves is the same row.
        assertThat(serviceabilityService.activeStore().orElseThrow().minOrderValue())
                .isEqualByComparingTo("199.00");
    }

    @Test
    void closeForTodayFlipsOpenOffAndReopenFlipsItBack() {
        StoreDto before = snapshot();
        serviceabilityService.updateStore(new StoreUpdateRequest(
                before.name(), before.address(), before.lat(), before.lng(),
                before.deliveryRadiusMeters(), LocalTime.of(0, 0), LocalTime.of(23, 59),
                before.minOrderValue(), before.supportPhone()));
        assertThat(serviceabilityService.activeStore().orElseThrow().open()).isTrue();

        StoreDto closed = serviceabilityService.closeForToday("Power cut");
        assertThat(closed.open()).as("closed despite being inside trading hours").isFalse();
        assertThat(closed.manuallyClosed()).isTrue();
        assertThat(closed.closedReason()).isEqualTo("Power cut");
        assertThat(closed.closedUntil()).isNotNull();
        // Runs to end of day, past closing time -> customers are told "tomorrow".
        assertThat(closed.opensNextDay()).isTrue();

        StoreDto reopened = serviceabilityService.reopen();
        assertThat(reopened.open()).isTrue();
        assertThat(reopened.manuallyClosed()).isFalse();
        assertThat(reopened.closedReason()).isNull();
    }

    @Test
    void aBlankSupportPhoneIsStoredAsNoNumber() {
        StoreDto b = snapshot();
        // The storefront offers a "call the store" link only when this is
        // present, so blank has to mean absent: an empty string would render a
        // call link to nowhere, which is the dead end this field exists to fix.
        StoreDto updated = serviceabilityService.updateStore(new StoreUpdateRequest(
                b.name(), b.address(), b.lat(), b.lng(), b.deliveryRadiusMeters(),
                b.openingTime(), b.closingTime(), b.minOrderValue(), "   "));

        assertThat(updated.supportPhone()).isNull();
    }

    @Test
    void aBlankReasonIsStoredAsNoReason() {
        snapshot();
        assertThat(serviceabilityService.closeForToday("   ").closedReason()).isNull();
    }

    @Test
    void invalidSettingsAreRejectedWholesale() {
        StoreDto b = snapshot();
        assertThatThrownBy(() -> serviceabilityService.updateStore(new StoreUpdateRequest(
                b.name(), b.address(), b.lat(), b.lng(), 100, b.openingTime(), b.closingTime(),
                b.minOrderValue(), b.supportPhone())))
                .as("radius below 500 m").isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> serviceabilityService.updateStore(new StoreUpdateRequest(
                b.name(), b.address(), b.lat(), b.lng(), b.deliveryRadiusMeters(),
                LocalTime.of(9, 0), LocalTime.of(9, 0), b.minOrderValue(), b.supportPhone())))
                .as("opening == closing").isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> serviceabilityService.updateStore(new StoreUpdateRequest(
                " ", b.address(), b.lat(), b.lng(), b.deliveryRadiusMeters(),
                b.openingTime(), b.closingTime(), b.minOrderValue(), b.supportPhone())))
                .as("blank name").isInstanceOf(IllegalArgumentException.class);
        // Nothing changed.
        assertThat(serviceabilityService.activeStore().orElseThrow().name()).isEqualTo(b.name());
    }
}
