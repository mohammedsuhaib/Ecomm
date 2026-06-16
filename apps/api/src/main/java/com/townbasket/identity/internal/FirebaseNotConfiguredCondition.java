package com.townbasket.identity.internal;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Matches only when {@code townbasket.identity.firebase.project-id} is ABSENT
 * or blank. Guards the {@link FakePhoneTokenVerifier} so the dev/fake verifier
 * is strictly the complement of {@link FirebasePhoneTokenVerifier} — exactly one
 * is active, and the fake can never run in a Firebase-configured (prod)
 * deployment. (A plain {@code @ConditionalOnProperty(matchIfMissing=true)} would
 * also match when the property is present, defeating the exclusivity.)
 */
class FirebaseNotConfiguredCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String projectId = context.getEnvironment()
                .getProperty("townbasket.identity.firebase.project-id");
        return projectId == null || projectId.isBlank();
    }
}
