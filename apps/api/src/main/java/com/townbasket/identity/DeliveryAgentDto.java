package com.townbasket.identity;

/**
 * Admin view of a delivery agent (rider). Carries the {@code active} flag so the
 * admin roster can show/deactivate agents; the assignment dropdown filters to
 * active ones. No password material is ever exposed.
 */
public record DeliveryAgentDto(
        Long id,
        String name,
        String email,
        boolean active,
        /** Rider's own availability; false means "don't assign me anything new right now". */
        boolean onDuty) {
}
