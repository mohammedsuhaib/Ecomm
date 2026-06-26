package com.townbasket.identity;

/**
 * Profile update payload for {@code PUT /api/v1/me}. {@code name} is required and
 * must be 1..80 characters after trimming; otherwise the request is rejected with
 * a 400.
 */
public record UpdateProfileRequest(String name) {
}
