package com.townbasket.identity.internal;

import com.townbasket.identity.InvalidCredentialsException;
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

    @Override
    public VerifiedPhone verify(String idToken) {
        if (idToken == null || !idToken.startsWith(DEV_PREFIX)) {
            throw new InvalidCredentialsException("Invalid phone token");
        }
        String phone = idToken.substring(DEV_PREFIX.length()).trim();
        if (!phone.matches("\\d{10}")) {
            throw new InvalidCredentialsException("Invalid phone token");
        }
        // Deterministic fake uid so repeat logins map to the same user.
        return new VerifiedPhone("dev-" + phone, phone);
    }
}
