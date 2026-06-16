package com.townbasket.orders;

import java.time.Instant;

/** A single entry in an order's status timeline. */
public record OrderTimelineEntryDto(String toStatus, Instant at) {
}
