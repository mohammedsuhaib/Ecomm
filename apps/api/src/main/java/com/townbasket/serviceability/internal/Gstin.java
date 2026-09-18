package com.townbasket.serviceability.internal;

/**
 * Normalisation and validation for a store's GSTIN, the 15-character GST
 * registration number that every tax invoice has to carry.
 *
 * <p>Module-internal helper in the style of {@code identity.internal.IndianPhone}:
 * one place that decides what a stored GSTIN looks like, so the admin write path
 * and anything reading the column later cannot disagree.
 *
 * <p><strong>What is checked, and what deliberately is not.</strong> A GSTIN is
 * 15 characters: a two-digit state code, a 10-character PAN, an entity code, a
 * fixed letter and a check digit. This class enforces the parts that cannot be
 * argued with — length, character set and a real state code — and stops there.
 *
 * <p>It does <em>not</em> verify the trailing check digit, though the algorithm
 * is well known, and it does not impose the PAN-shaped regex
 * ({@code [A-Z]{5}[0-9]{4}[A-Z]}) that regular registrations follow. Both would
 * catch more typos, and both risk refusing a real registration: the strict
 * pattern does not fit every category the department issues (TDS, UIN and OIDAR
 * registrations are shaped differently), and a validator that rejects a valid
 * GSTIN does not merely annoy — it leaves a registered store unable to save its
 * number and therefore unable to issue a compliant invoice at all. That is a
 * worse failure than storing a typo, which a human reads back off the invoice
 * and corrects. If the check digit is wanted later, implement it as a warning
 * the admin card shows, not as a refusal.
 */
final class Gstin {

    /** GSTINs are 15 characters, uppercase letters and digits only. */
    private static final int LENGTH = 15;

    private Gstin() {
    }

    /**
     * Clean up a GSTIN as staff typed it: trim, strip internal spaces and
     * hyphens (it is often written in groups when copied off a certificate),
     * and uppercase it. Returns {@code null} for blank input, which is how "no
     * GSTIN yet" is stored.
     */
    static String normalise(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.replaceAll("[\\s-]", "").toUpperCase();
        return cleaned.isEmpty() ? null : cleaned;
    }

    /**
     * Validate an already-{@link #normalise(String)}d GSTIN.
     *
     * @throws IllegalArgumentException with a message written for the staff
     *     member reading it in the admin card (mapped to 400)
     */
    static void validate(String gstin) {
        if (gstin == null) {
            return; // not registered yet — allowed, the invoice omits the line
        }
        if (gstin.length() != LENGTH) {
            throw new IllegalArgumentException(
                    "A GSTIN is 15 characters; this one has " + gstin.length() + ".");
        }
        for (int i = 0; i < LENGTH; i++) {
            char c = gstin.charAt(i);
            boolean allowed = (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z');
            if (!allowed) {
                throw new IllegalArgumentException(
                        "A GSTIN contains only letters and digits; remove the '" + c + "'.");
            }
        }
        if (!isKnownStateCode(gstin.substring(0, 2))) {
            throw new IllegalArgumentException(
                    "A GSTIN starts with a state code (Karnataka is 29); '"
                            + gstin.substring(0, 2) + "' is not one.");
        }
    }

    /**
     * The first two digits are the GST state code. 01–38 are the states and
     * union territories; 97 is "other territory" and 99 is used for Centre
     * jurisdiction, both of which are real and issued, so neither is refused.
     */
    private static boolean isKnownStateCode(String code) {
        int state;
        try {
            state = Integer.parseInt(code);
        } catch (NumberFormatException e) {
            return false;
        }
        return (state >= 1 && state <= 38) || state == 97 || state == 99;
    }
}
