package com.townbasket.identity.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * JPA entity for {@code identity.refresh_tokens}. Module-internal. Stores only
 * the SHA-256 {@code token_hash} of the opaque refresh token (never the raw
 * value); rotates on use and is revoked on logout. Carries a plain
 * {@code user_id}.
 */
@Entity
@Table(name = "refresh_tokens", schema = "identity")
class RefreshTokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected RefreshTokenEntity() {
        // JPA
    }

    RefreshTokenEntity(Long userId, String tokenHash, Instant expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.revoked = false;
    }

    Long getId() {
        return id;
    }

    Long getUserId() {
        return userId;
    }

    Instant getExpiresAt() {
        return expiresAt;
    }

    boolean isRevoked() {
        return revoked;
    }

    void revoke() {
        this.revoked = true;
    }

    boolean isUsable(Instant now) {
        return !revoked && expiresAt.isAfter(now);
    }
}
