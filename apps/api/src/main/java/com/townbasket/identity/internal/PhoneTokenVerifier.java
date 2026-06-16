package com.townbasket.identity.internal;

/**
 * Port hiding the phone-OTP auth vendor (Firebase) behind the identity module.
 * The rest of the app never sees Firebase — it only ever receives a verified
 * {@code (firebaseUid, phoneE164)} pair. Two implementations:
 * {@link FakePhoneTokenVerifier} (dev/test, accepts {@code dev:<phone>}) and
 * {@link FirebasePhoneTokenVerifier} (prod, verifies the Google-signed token).
 * Exactly one is active, selected by {@code @ConditionalOnProperty}.
 */
interface PhoneTokenVerifier {

    /**
     * Verify a Firebase ID token (or dev token) and return the identity.
     *
     * @throws com.townbasket.identity.InvalidCredentialsException if invalid/expired
     */
    VerifiedPhone verify(String idToken);

    /** The verified result: the vendor's stable uid and the E.164 phone number. */
    record VerifiedPhone(String firebaseUid, String phone) {
    }
}
