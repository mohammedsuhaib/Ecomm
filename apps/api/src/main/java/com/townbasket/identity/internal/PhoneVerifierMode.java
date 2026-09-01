package com.townbasket.identity.internal;

/**
 * Decides which {@link PhoneTokenVerifier} a deployment gets from the single
 * property {@code townbasket.identity.firebase.project-id}.
 *
 * <p>Three states, not two, because the middle one is a real deployment mistake
 * that must never be guessed at:
 *
 * <ul>
 *   <li><b>absent</b> → {@link #FAKE}: the offline verifier that accepts only
 *       {@code dev:<10-digit-phone>}. The default for local dev and QA.</li>
 *   <li><b>non-blank</b> → {@link #REAL}: Google-signed-token verification.</li>
 *   <li><b>present but blank</b> → refuse to start. Treating blank as FAKE would
 *       silently downgrade a production deployment to a verifier that accepts a
 *       hand-typed token for any phone number — the exact bypass the split
 *       exists to prevent. Treating it as REAL would verify tokens against an
 *       empty issuer, so every login fails with no useful message. An empty env
 *       var is easy to produce by accident (an unset variable interpolated into
 *       a compose file becomes ""), so it gets a named error instead.</li>
 * </ul>
 */
enum PhoneVerifierMode {
    FAKE,
    REAL;

    /**
     * @param projectId raw property value, {@code null} when unset
     * @throws IllegalStateException when the property is present but blank
     */
    static PhoneVerifierMode of(String projectId) {
        if (projectId == null) {
            return FAKE;
        }
        if (projectId.isBlank()) {
            throw new IllegalStateException(
                    "townbasket.identity.firebase.project-id is set but blank. "
                            + "Set a real Firebase projectId to enable phone-OTP, or remove the "
                            + "property entirely (e.g. drop FIREBASE_PROJECT_ID / "
                            + "TOWNBASKET_IDENTITY_FIREBASE_PROJECT_ID from the environment) to use "
                            + "the offline dev verifier. Blank is refused because it would otherwise "
                            + "either downgrade a real deployment to the dev verifier or verify "
                            + "tokens against an empty issuer.");
        }
        return REAL;
    }
}
