package com.townbasket.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The one rule that keeps a hand-typed {@code dev:<phone>} token out of a real
 * deployment. Offline — no Docker, no Spring context.
 */
class PhoneVerifierModeTest {

    @Test
    void absentProjectIdUsesTheOfflineFake() {
        assertThat(PhoneVerifierMode.of(null)).isEqualTo(PhoneVerifierMode.FAKE);
    }

    @Test
    void aRealProjectIdUsesGoogleTokenVerification() {
        assertThat(PhoneVerifierMode.of("town-basket")).isEqualTo(PhoneVerifierMode.REAL);
    }

    /**
     * An unset variable interpolated into a compose file arrives as "". Blank
     * must never resolve to FAKE: that would silently downgrade production to a
     * verifier accepting any phone number without an SMS.
     */
    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t", "\n", "   "})
    void blankIsRefusedRatherThanGuessed(String blank) {
        assertThatThrownBy(() -> PhoneVerifierMode.of(blank))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("project-id is set but blank")
                .hasMessageContaining("offline dev verifier");
    }
}
