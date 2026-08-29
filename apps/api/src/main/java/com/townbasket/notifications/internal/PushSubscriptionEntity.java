package com.townbasket.notifications.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * JPA entity for {@code notifications.push_subscriptions}. Module-internal.
 *
 * <p>One row per browser/device a customer has allowed notifications on.
 * {@code p256dh} and {@code auth} are that subscription's public key material,
 * used to encrypt each payload — per-subscription secrets that must never be
 * returned by any API response.
 */
@Entity
@Table(name = "push_subscriptions", schema = "notifications")
class PushSubscriptionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, updatable = false)
    private String endpoint;

    @Column(nullable = false)
    private String p256dh;

    @Column(nullable = false)
    private String auth;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_sent_at")
    private Instant lastSentAt;

    protected PushSubscriptionEntity() {
        // JPA
    }

    PushSubscriptionEntity(Long userId, String endpoint, String p256dh, String auth, String userAgent) {
        this.userId = userId;
        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.auth = auth;
        this.userAgent = userAgent;
    }

    Long getId() {
        return id;
    }

    Long getUserId() {
        return userId;
    }

    String getEndpoint() {
        return endpoint;
    }

    /** Per-subscription key material — never serialize to a client. */
    String getP256dh() {
        return p256dh;
    }

    /** Per-subscription key material — never serialize to a client. */
    String getAuth() {
        return auth;
    }

    void setUserId(Long userId) {
        this.userId = userId;
    }

    void setP256dh(String p256dh) {
        this.p256dh = p256dh;
    }

    void setAuth(String auth) {
        this.auth = auth;
    }

    void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    void setLastSentAt(Instant lastSentAt) {
        this.lastSentAt = lastSentAt;
    }
}
