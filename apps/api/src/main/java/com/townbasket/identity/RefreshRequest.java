package com.townbasket.identity;

/** Token-refresh request: the raw refresh token to rotate. */
public record RefreshRequest(String refreshToken) {
}
