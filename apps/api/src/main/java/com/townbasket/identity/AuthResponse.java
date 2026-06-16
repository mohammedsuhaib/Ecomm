package com.townbasket.identity;

/**
 * Login/signup result: a fresh access+refresh token pair plus the authenticated
 * user. The raw refresh token is returned only here (and on refresh); only its
 * SHA-256 hash is persisted.
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        UserDto user) {
}
