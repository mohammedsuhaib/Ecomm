package com.townbasket.notifications.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * VAPID configuration for the Web Push channel.
 *
 * <p>Both keys are base64url-encoded and come as a matched pair — generate one
 * with {@code npx web-push generate-vapid-keys} (see {@code NOTIFICATIONS.md}).
 * The <strong>public</strong> key is handed to browsers so they can mint a
 * subscription bound to this server; the <strong>private</strong> key signs each
 * push request and must be treated like any other secret (env var, never
 * committed). When either is blank the channel reports itself disabled and the
 * app runs exactly as before — that is the default for local development.
 *
 * @param publicKey  base64url VAPID public key (safe to expose to browsers)
 * @param privateKey base64url VAPID private key (secret)
 * @param subject    {@code mailto:} or {@code https:} contact the push service
 *                   can use to reach the operator about a misbehaving sender
 */
@ConfigurationProperties(prefix = "townbasket.notifications.push")
record WebPushProperties(String publicKey, String privateKey, String subject) {

    WebPushProperties {
        publicKey = publicKey == null ? "" : publicKey.trim();
        privateKey = privateKey == null ? "" : privateKey.trim();
        subject = subject == null || subject.isBlank() ? "mailto:support@town-basket.com" : subject.trim();
    }

    /** Push can only be sent when a complete VAPID key pair is configured. */
    boolean configured() {
        return !publicKey.isEmpty() && !privateKey.isEmpty();
    }
}
