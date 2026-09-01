package com.townbasket.identity.internal;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Matches only when {@code townbasket.identity.firebase.project-id} is ABSENT,
 * guarding {@link FakePhoneTokenVerifier} so the dev/fake verifier is the strict
 * complement of {@link FirebasePhoneTokenVerifier} — exactly one is ever active,
 * and the fake can never run in a Firebase-configured (prod) deployment.
 *
 * <p>Both conditions route through {@link PhoneVerifierMode}, which rejects a
 * present-but-blank value. Conditions are evaluated during bean-definition
 * registration, before any bean is instantiated, so a blank value fails the boot
 * with a named error rather than an "no unique bean of type PhoneTokenVerifier"
 * further down.
 */
class FirebaseNotConfiguredCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return PhoneVerifierMode.of(projectId(context)) == PhoneVerifierMode.FAKE;
    }

    static String projectId(ConditionContext context) {
        return context.getEnvironment().getProperty("townbasket.identity.firebase.project-id");
    }
}
