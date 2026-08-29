package com.townbasket.notifications;

/**
 * What a client needs to decide whether to offer push and how to subscribe.
 *
 * @param enabled   {@code false} when the deployment has no VAPID keys, so the
 *                  UI hides the opt-in entirely instead of failing on subscribe
 * @param publicKey base64url VAPID public key ({@code null} when disabled) —
 *                  public by design: it only lets a browser mint a subscription
 *                  addressed to this server
 */
public record PushConfigDto(boolean enabled, String publicKey) {
}
