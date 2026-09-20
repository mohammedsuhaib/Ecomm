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
                original.minOrderValue(), original.supportPhone(), original.gstin()));
    }

    @Test
    void settingsRoundTripAndTheNewHoursApplyImmediately() {
        StoreDto before = snapshot();

        // Open 24h so the assertion cannot depend on the wall clock.
        StoreDto updated = serviceabilityService.updateStore(new StoreUpdateRequest(
                "Town Basket QA", before.address(), before.lat(), before.lng(),
                7_000, LocalTime.of(0, 0), LocalTime.of(23, 59), new BigDecimal("199.00"),
                "08212345678", before.gstin()));

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
                before.minOrderValue(), before.supportPhone(), before.gstin()));
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
                b.openingTime(), b.closingTime(), b.minOrderValue(), "   ", b.gstin()));

        assertThat(updated.supportPhone()).isNull();
    }

    @Test
    void theGstinRoundTripsAndIsNormalisedAndValidated() {
        StoreDto b = snapshot();

        // Accepted as it appears on a registration certificate — spaced, and
        // whatever case it was pasted in — and stored in one canonical form,
        // because it is printed on invoices.
        StoreDto updated = serviceabilityService.updateStore(new StoreUpdateRequest(
                b.name(), b.address(), b.lat(), b.lng(), b.deliveryRadiusMeters(),
                b.openingTime(), b.closingTime(), b.minOrderValue(), b.supportPhone(),
                "29 aapfu 0939 f1zv"));
        assertThat(updated.gstin()).isEqualTo("29AAPFU0939F1ZV");
        // The public /store endpoint serves the same row: a GSTIN is public by
        // law, so this is not a leak.
        assertThat(serviceabilityService.activeStore().orElseThrow().gstin())
                .isEqualTo("29AAPFU0939F1ZV");

        // Blank clears it — an unregistered store still has to save the card.
        StoreDto cleared = serviceabilityService.updateStore(new StoreUpdateRequest(
                b.name(), b.address(), b.lat(), b.lng(), b.deliveryRadiusMeters(),
                b.openingTime(), b.closingTime(), b.minOrderValue(), b.supportPhone(), "  "));
        assertThat(cleared.gstin()).isNull();

        // Malformed is refused at the point staff can still fix it, rather than
        // reaching an invoice.
        assertThatThrownBy(() -> serviceabilityService.updateStore(new StoreUpdateRequest(
                b.name(), b.address(), b.lat(), b.lng(), b.deliveryRadiusMeters(),
                b.openingTime(), b.closingTime(), b.minOrderValue(), b.supportPhone(),
                "29AAPFU0939F1Z")))
                .as("14 characters").isInstanceOf(IllegalArgumentException.class);
        assertThat(serviceabilityService.activeStore().orElseThrow().gstin()).isNull();
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
                b.minOrderValue(), b.supportPhone(), b.gstin())))
                .as("radius below 500 m").isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> serviceabilityService.updateStore(new StoreUpdateRequest(
                b.name(), b.address(), b.lat(), b.lng(), b.deliveryRadiusMeters(),
                LocalTime.of(9, 0), LocalTime.of(9, 0), b.minOrderValue(), b.supportPhone(), b.gstin())))
                .as("opening == closing").isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> serviceabilityService.updateStore(new StoreUpdateRequest(
                " ", b.address(), b.lat(), b.lng(), b.deliveryRadiusMeters(),
                b.openingTime(), b.closingTime(), b.minOrderValue(), b.supportPhone(), b.gstin())))
                .as("blank name").isInstanceOf(IllegalArgumentException.class);
        // Nothing changed.
        assertThat(serviceabilityService.activeStore().orElseThrow().name()).isEqualTo(b.name());
    }
}
