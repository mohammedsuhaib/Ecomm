package com.townbasket.shared;

import java.time.Instant;

/**
 * Consistent error model returned by the API. Lives in the OPEN {@code shared}
 * module so every module's controllers and the global handler share one shape.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path) {

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path);
    }
}
