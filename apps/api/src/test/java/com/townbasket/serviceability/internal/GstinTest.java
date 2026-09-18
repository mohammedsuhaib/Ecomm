package com.townbasket.serviceability.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * What a stored GSTIN is allowed to look like. Worth pinning because the value
 * ends up printed on legal documents, and because both failure directions are
 * costly: accepting junk puts a wrong number on every invoice, while refusing a
 * real registration leaves a registered store unable to invoice at all.
 */
class GstinTest {

    /** A published sample GSTIN, used here as a realistic well-formed value. */
    private static final String VALID = "27AAPFU0939F1ZV";

    @Test
    void acceptsAWellFormedGstin() {
        String normalised = Gstin.normalise(VALID);
        assertThatCode(() -> Gstin.validate(normalised)).doesNotThrowAnyException();
        assertThat(normalised).isEqualTo(VALID);
    }

    @Test
    void normalisesHowStaffActuallyTypeIt() {
        // Copied off a registration certificate, where it is often spaced or
        // hyphenated, and pasted in lowercase.
        assertThat(Gstin.normalise("  27aapfu0939f1zv ")).isEqualTo(VALID);
        assertThat(Gstin.normalise("27 AAPFU 0939 F1ZV")).isEqualTo(VALID);
        assertThat(Gstin.normalise("27-AAPFU-0939-F1ZV")).isEqualTo(VALID);
    }

    @Test
    void blankMeansNotRegisteredRatherThanInvalid() {
        // A store that has not registered yet must still be able to save the
        // rest of the admin card, so blank is a value, not an error.
        assertThat(Gstin.normalise(null)).isNull();
        assertThat(Gstin.normalise("")).isNull();
        assertThat(Gstin.normalise("   ")).isNull();
        assertThatCode(() -> Gstin.validate(null)).doesNotThrowAnyException();
    }

    @Test
    void rejectsTheWrongLength() {
        assertThatThrownBy(() -> Gstin.validate(Gstin.normalise("27AAPFU0939F1Z")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("15 characters");
        assertThatThrownBy(() -> Gstin.validate(Gstin.normalise("27AAPFU0939F1ZVX")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("15 characters");
    }

    @Test
    void rejectsCharactersAGstinCannotContain() {
        assertThatThrownBy(() -> Gstin.validate(Gstin.normalise("27AAPFU0939F1Z*")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("letters and digits");
    }

    @Test
    void rejectsAStateCodeThatIsNotIssued() {
        // 00 and anything past 38 (other than the 97/99 specials) is not a
        // state, so it is a typo in the one part of the number that is trivial
        // to check.
        assertThatThrownBy(() -> Gstin.validate(Gstin.normalise("00AAPFU0939F1ZV")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("state code");
        assertThatThrownBy(() -> Gstin.validate(Gstin.normalise("54AAPFU0939F1ZV")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("state code");
    }

    @Test
    void acceptsEveryStateCodeThatIsIssued() {
        // 01-38 are the states and union territories; 97 is "other territory"
        // and 99 is Centre jurisdiction. Refusing any of them would block a
        // real registration, which is the failure this class is most careful
        // about — so all of them are checked, not just Karnataka's 29.
        for (int state = 1; state <= 38; state++) {
            String candidate = String.format("%02d", state) + VALID.substring(2);
            assertThatCode(() -> Gstin.validate(candidate))
                    .as("state code %02d", state)
                    .doesNotThrowAnyException();
        }
        assertThatCode(() -> Gstin.validate("97" + VALID.substring(2))).doesNotThrowAnyException();
        assertThatCode(() -> Gstin.validate("99" + VALID.substring(2))).doesNotThrowAnyException();
    }
}
