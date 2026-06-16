package com.townbasket.identity;

/**
 * A rotated access+refresh token pair, returned by {@code POST /auth/refresh}.
 * The presented refresh token is revoked and replaced by the one returned here.
 */
public record TokenPair(
        String accessToken,
        String refreshToken) {
}
