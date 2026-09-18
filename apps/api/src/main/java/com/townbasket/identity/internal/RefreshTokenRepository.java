package com.townbasket.identity.internal;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Module-internal Spring Data repository for refresh tokens (looked up by hash). */
interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, Long> {

    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

    /**
     * Whether the user still holds a usable refresh token — i.e. is signed in
     * somewhere. A session ends by logout (the presented token is revoked), by
     * expiry, or by a family revoke (password reset, deactivation, token reuse),
     * and all three leave nothing that matches. Rotation keeps exactly one live
     * token per session, so this is true for the whole life of a session.
     */
    boolean existsByUserIdAndRevokedFalseAndExpiresAtAfter(Long userId, Instant now);

    /**
     * Revoke every still-usable token for a user in one statement. Used by the
     * reuse-detection path: presenting an already-revoked (rotated) token is the
     * classic sign of a stolen token, so the whole family is invalidated.
     * Returns the number of tokens revoked.
     */
    @Modifying
    @Query("update RefreshTokenEntity t set t.revoked = true where t.userId = :userId and t.revoked = false")
    int revokeAllByUserId(@Param("userId") Long userId);

    /**
     * Bulk-delete spent tokens (revoked, or past expiry as of {@code cutoff}).
     * Keeps {@code identity.refresh_tokens} bounded under the rotate-on-use
     * scheme; called from a scheduled job. Returns the number of rows removed.
     */
    @Modifying
    @Query("delete from RefreshTokenEntity t where t.revoked = true or t.expiresAt < :cutoff")
    int deleteRevokedOrExpiredBefore(@Param("cutoff") Instant cutoff);
}
