package com.townbasket.identity.internal;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Matches only when {@code townbasket.identity.firebase.project-id} holds a
 * NON-BLANK value, guarding {@link FirebasePhoneTokenVerifier}.
 *
 * <p>Replaces a plain {@code @ConditionalOnProperty}, which counts an empty
 * string as "present" and so activated the real verifier with an empty issuer
 * while {@link FirebaseNotConfiguredCondition} simultaneously activated the
 * fake — two beans, no unique {@link PhoneTokenVerifier}, and a boot failure
 * that named neither the property nor the fix.
 */
class FirebaseConfiguredCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return PhoneVerifierMode.of(FirebaseNotConfiguredCondition.projectId(context))
                == PhoneVerifierMode.REAL;
    }
}
