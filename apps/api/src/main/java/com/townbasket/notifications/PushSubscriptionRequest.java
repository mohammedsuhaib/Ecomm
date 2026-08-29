package com.townbasket.notifications;

/**
 * A browser push subscription, in the exact shape
 * {@code PushSubscription.toJSON()} produces — the storefront posts it
 * unmodified.
 *
 * @param endpoint the push service URL for this browser/device
 * @param keys     the subscription's public key material used to encrypt payloads
 */
public record PushSubscriptionRequest(String endpoint, Keys keys) {

    /**
     * @param p256dh the subscription's P-256 public key (base64url)
     * @param auth   the subscription's auth secret (base64url)
     */
    public record Keys(String p256dh, String auth) {
    }
}
