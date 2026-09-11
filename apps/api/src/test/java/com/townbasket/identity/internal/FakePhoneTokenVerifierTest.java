package com.townbasket.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.townbasket.identity.InvalidCredentialsException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Offline verifier: what it accepts, and everything it must refuse. */
class FakePhoneTokenVerifierTest {

    private final FakePhoneTokenVerifier verifier = new FakePhoneTokenVerifier();

    @Test
    void acceptsADevTokenAndDerivesAStableUid() {
        PhoneTokenVerifier.VerifiedPhone verified = verifier.verify("dev:9876500001");

        assertThat(verified.phone()).isEqualTo("9876500001");
        // Deterministic, so repeat logins map to one user rather than piling up.
        assertThat(verified.firebaseUid()).isEqualTo("dev-9876500001");
        assertThat(verifier.verify("dev:9876500001").firebaseUid()).isEqualTo(verified.firebaseUid());
    }

    /**
     * A real Firebase ID token must never be honoured by the offline verifier —
     * that is the whole point of the split. (A three-segment JWT shape is also
     * what triggers the "you are half-configured" log line.)
     */
    @Test
    void refusesARealLookingFirebaseIdToken() {
        String jwtShaped = "eyJhbGciOiJSUzI1NiIsImtpZCI6ImFiYyJ9.eyJzdWIiOiJ1aWQifQ.c2lnbmF0dXJl";

        assertThatThrownBy(() -> verifier.verify(jwtShaped))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid phone token");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "9876500001",        // no dev: prefix
        "dev:98765",         // too short
        "dev:98765000012",   // too long
        "dev:98765abcde",    // not digits
        "DEV:9876500001",    // prefix is case-sensitive
        "dev:",              // nothing after the prefix
    })
    void refusesEverythingElse(String token) {
        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void refusesANullToken() {
        assertThatThrownBy(() -> verifier.verify(null))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
