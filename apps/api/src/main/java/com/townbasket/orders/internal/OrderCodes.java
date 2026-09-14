package com.townbasket.orders.internal;

import java.security.SecureRandom;

/**
 * The short, human-speakable order code shown to customers and read back over
 * the phone — see {@code V6_8__orders_public_code.sql} for why the sequential
 * id and the tracking UUID are both unfit for that job.
 *
 * <p>Eight symbols of <a href="https://www.crockford.com/base32.html">Crockford
 * base32</a>: the alphabet drops I, L, O and U, so nothing in a code can be
 * misheard as something else or misread off a printed slip, and {@link
 * #normalize(String)} folds the mistakes people make anyway (lower case,
 * hyphens, typing {@code I} for {@code 1} or {@code O} for {@code 0}) back onto
 * the stored form so a staff search matches what the customer dictates.
 *
 * <p>Eight symbols is ~40 bits. That is not a secret and is not treated as one:
 * order access is authorised by login plus ownership, and the URL handle is
 * still {@code public_token}. The entropy only has to make a code
 * non-enumerable and collision-free at this store's volume, which it does by a
 * wide margin.
 */
final class OrderCodes {

    /** Crockford base32 — no I, L, O or U. */
    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();

    static final int CODE_LENGTH = 8;

    private static final SecureRandom RANDOM = new SecureRandom();

    private OrderCodes() {
    }

    /**
     * A fresh random code. Callers must treat a unique-constraint violation as
     * a collision and retry — at 40 bits that is vanishingly rare, but the
     * database, not this method, is the arbiter of uniqueness.
     */
    static String newCode() {
        StringBuilder out = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            out.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return out.toString();
    }

    /**
     * Fold a human-typed code onto its stored form: upper-case, hyphens and
     * spaces dropped, and the Crockford substitutions applied (I/L read as 1,
     * O as 0). Anything still outside the alphabet is dropped, so the result is
     * safe to use in a LIKE pattern. Never null; may be empty.
     */
    static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String upper = raw.toUpperCase(java.util.Locale.ROOT);
        StringBuilder out = new StringBuilder(upper.length());
        for (int i = 0; i < upper.length(); i++) {
            char c = upper.charAt(i);
            char folded = switch (c) {
                case 'I', 'L' -> '1';
                case 'O' -> '0';
                default -> c;
            };
            if (isAlphabet(folded)) {
                out.append(folded);
            }
        }
        return out.toString();
    }

    private static boolean isAlphabet(char c) {
        for (char valid : ALPHABET) {
            if (valid == c) {
                return true;
            }
        }
        return false;
    }
}
