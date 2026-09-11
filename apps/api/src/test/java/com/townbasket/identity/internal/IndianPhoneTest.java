package com.townbasket.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.townbasket.identity.InvalidCredentialsException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * One canonical shape, whichever verifier produced the number. Offline — no
 * Docker, no Spring context.
 */
class IndianPhoneTest {

    @ParameterizedTest
    @CsvSource({
        "9632500797,     9632500797",   // already canonical (offline verifier)
        "+919632500797,  9632500797",   // E.164 (Firebase)
        "919632500797,   9632500797",   // E.164 without the +
        "+91 96325 00797,9632500797",   // spaced as a human would type it
        "+91-96325-00797,9632500797",   // dashed
        "09632500797,    9632500797",   // local dialling form
    })
    void reducesEveryIndianFormToTenDigits(String raw, String expected) {
        assertThat(IndianPhone.normalise(raw)).isEqualTo(expected);
    }

    /**
     * The trap in stripping a "91" prefix: a valid bare mobile can start with
     * those digits. Only a 12-digit value may lose them.
     */
    @Test
    void keepsATenDigitMobileThatHappensToStartWith91() {
        assertThat(IndianPhone.normalise("9123456789")).isEqualTo("9123456789");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "98765",             // too short
        "98765000123456",    // too long
        "+14155552671",      // not Indian — refused rather than stored unusable
        "abcdefghij",
    })
    void refusesAnythingThatIsNotAnIndianMobile(String raw) {
        assertThatThrownBy(() -> IndianPhone.normalise(raw))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void refusesNull() {
        assertThatThrownBy(() -> IndianPhone.normalise(null))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("missing");
    }
}
