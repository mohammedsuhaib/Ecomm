package com.townbasket.orders;

import java.time.Instant;

/**
 * A single entry in an order's status timeline. {@code note} is the reason
 * recorded with the transition (why a delivery failed, why staff cancelled) —
 * null for routine steps. Customer-facing surfaces also receive it: the
 * failure reason is exactly what a customer waiting for groceries wants to
 * read, and nothing sensitive is ever written there.
 */
public record OrderTimelineEntryDto(String toStatus, Instant at, String note) {
}
