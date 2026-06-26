package com.townbasket.identity;

/**
 * Customer phone login/signup request: a Firebase ID token from the client's
 * OTP flow. In dev (no Firebase configured) this is a {@code dev:<phone>} token.
 */
public record PhoneVerifyRequest(String firebaseIdToken) {
}
