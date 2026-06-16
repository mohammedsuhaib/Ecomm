package com.townbasket.identity;

/** Logout request: the raw refresh token to revoke (idempotent). */
public record LogoutRequest(String refreshToken) {
}
