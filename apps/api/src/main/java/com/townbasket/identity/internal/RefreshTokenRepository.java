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
     * Bulk-delete spent tokens (revoked, or past expiry as of {@code cutoff}).
     * Keeps {@code identity.refresh_tokens} bounded under the rotate-on-use
     * scheme; called from a scheduled job. Returns the number of rows removed.
     */
    @Modifying
    @Query("delete from RefreshTokenEntity t where t.revoked = true or t.expiresAt < :cutoff")
    int deleteRevokedOrExpiredBefore(@Param("cutoff") Instant cutoff);
}
