package com.townbasket.identity.internal;

import com.townbasket.identity.InvalidCredentialsException;

/**
 * Canonicalises a phone number to the BARE 10-DIGIT national form the rest of
 * the app assumes: order placement validates {@code [0-9]{10}}, the seeds and
 * the offline {@code dev:<phone>} token use 10 digits, and every UI renders the
 * {@code +91} prefix itself.
 *
 * <p>This exists because the two phone verifiers hand back different shapes for
 * the same human. The offline verifier yields {@code 9632500797}; Firebase yields
 * the E.164 {@code +919632500797}. Storing both verbatim meant switching a
 * deployment to real OTP silently created a SECOND user row for an existing
 * customer — orders are linked by user id, so their history disappeared — and the
 * stored value then failed checkout's 10-digit validation, leaving a signed-in
 * customer unable to order at all.
 *
 * <p>India-only by design, matching the single-store 5 km delivery area and the
 * 10-digit validation already enforced at checkout. A number that cannot be
 * reduced to 10 digits is refused at login rather than stored in a shape that
 * breaks ordering later.
 */
final class IndianPhone {

    private IndianPhone() {
    }

    /**
     * @param raw as supplied by a verifier: E.164, spaced, or already bare
     * @return exactly 10 digits
     * @throws InvalidCredentialsException when {@code raw} is not an Indian number
     */
    static String normalise(String raw) {
        if (raw == null) {
            throw new InvalidCredentialsException("Phone number missing from verified token");
        }
        String digits = raw.replaceAll("[^0-9]", "");

        // 91 + 10 digits (E.164, with or without the leading +). Length is what
        // makes this safe: a bare 10-digit mobile may itself start "91"
        // (9123456789 is valid), so the prefix alone must never be stripped.
        if (digits.length() == 12 && digits.startsWith("91")) {
            digits = digits.substring(2);
        } else if (digits.length() == 11 && digits.startsWith("0")) {
            // Local dialling form, 0XXXXXXXXXX.
            digits = digits.substring(1);
        }

        if (digits.length() != 10) {
            throw new InvalidCredentialsException(
                    "Phone number is not a 10-digit Indian mobile number");
        }
        return digits;
    }
}
