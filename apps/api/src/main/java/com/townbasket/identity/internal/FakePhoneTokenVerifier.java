package com.townbasket.identity.internal;

import com.townbasket.identity.InvalidCredentialsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * Dev/test phone-token verifier. Active ONLY when
 * {@code townbasket.identity.firebase.project-id} is ABSENT (the default in
 * dev/test). Accepts exclusively {@code dev:<10-digit-phone>} tokens; anything
 * else is rejected.
 *
 * <p><strong>Hard security requirement:</strong> this bean must be inactive
 * when Firebase is configured, so a production deployment cannot be bypassed
 * with a {@code dev:} token. {@link FirebaseNotConfiguredCondition} and
 * {@link FirebaseConfiguredCondition} both decide via {@link PhoneVerifierMode},
 * so they are strict complements — exactly one of the two beans is active, and a
 * present-but-blank project-id is refused rather than resolved to this one.
 */
@Component
@Conditional(FirebaseNotConfiguredCondition.class)
class FakePhoneTokenVerifier implements PhoneTokenVerifier {

    private static final String DEV_PREFIX = "dev:";

    private static final Logger log = LoggerFactory.getLogger(FakePhoneTokenVerifier.class);

    FakePhoneTokenVerifier() {
        // Which verifier is live is the first thing you need when a login 401s,
        // and the response deliberately does not say.
        log.info("Phone-OTP: OFFLINE dev verifier active (accepts only dev:<10-digit-phone>). "
                + "Set townbasket.identity.firebase.project-id to require real Firebase tokens.");
    }

    @Override
    public VerifiedPhone verify(String idToken) {
        if (idToken == null || !idToken.startsWith(DEV_PREFIX)) {
            if (looksLikeAJwt(idToken)) {
                // The commonest half-configured deployment: the storefront was
                // built with the Firebase web config and is sending a real ID
                // token, but the API never got a projectId. Say that, rather
                // than leaving an "Invalid phone token" 401 with no cause.
                log.warn("Phone token rejected: this looks like a real Firebase ID token, but the "
                        + "OFFLINE verifier is active. Set "
                        + "townbasket.identity.firebase.project-id (env "
                        + "TOWNBASKET_IDENTITY_FIREBASE_PROJECT_ID) to the projectId the frontend "
                        + "was built with, and restart the API.");
            } else {
                log.warn("Phone token rejected: the offline verifier accepts only "
                        + "dev:<10-digit-phone>");
            }
            throw new InvalidCredentialsException("Invalid phone token");
        }
        String phone = idToken.substring(DEV_PREFIX.length()).trim();
        if (!phone.matches("\\d{10}")) {
            log.warn("Phone token rejected: dev: prefix present but the phone is not 10 digits");
            throw new InvalidCredentialsException("Invalid phone token");
        }
        // Deterministic fake uid so repeat logins map to the same user.
        return new VerifiedPhone("dev-" + phone, phone);
    }

    /**
     * A Firebase ID token is a JWT: three non-empty dot-separated segments,
     * starting with a base64url header. Used only to write a better log line —
     * never to accept anything.
     */
    private static boolean looksLikeAJwt(String token) {
        if (token == null) {
            return false;
        }
        String[] parts = token.split("\\.");
        return parts.length == 3 && !parts[0].isBlank() && !parts[1].isBlank() && !parts[2].isBlank();
    }
}
