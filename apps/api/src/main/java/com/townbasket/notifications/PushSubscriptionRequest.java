package com.townbasket.notifications;

/**
 * A browser push subscription, in the exact shape
 * {@code PushSubscription.toJSON()} produces — the storefront posts it
 * unmodified.
 *
 * @param endpoint the push service URL for this browser/device
 * @param keys     the subscription's public key material used to encrypt payloads
 * @param audience optional; {@code "STAFF"} from the admin dashboard to receive
 *                 store-wide order alerts, absent for everyone else
 */
public record PushSubscriptionRequest(String endpoint, Keys keys, String audience) {

    /** Audience value for a device that wants the store-wide staff alerts. */
    public static final String STAFF = "STAFF";

    /** A subscription for the caller's own updates (customer or rider). */
    public PushSubscriptionRequest(String endpoint, Keys keys) {
        this(endpoint, keys, null);
    }

    /** Whether the device asked for the store-wide staff alerts. */
    public boolean forStaff() {
        return STAFF.equalsIgnoreCase(audience);
    }

    /**
     * @param p256dh the subscription's P-256 public key (base64url)
     * @param auth   the subscription's auth secret (base64url)
     */
    public record Keys(String p256dh, String auth) {
    }
}
