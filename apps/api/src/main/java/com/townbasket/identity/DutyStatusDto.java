package com.townbasket.identity;

/** {@code GET/PUT /me/duty} — the calling rider's own availability. */
public record DutyStatusDto(boolean onDuty) {
}
