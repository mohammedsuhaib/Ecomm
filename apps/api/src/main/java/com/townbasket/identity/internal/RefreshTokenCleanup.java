package com.townbasket.identity.internal;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Periodically purges spent refresh tokens — revoked, or past expiry — so
 * {@code identity.refresh_tokens} does not grow unbounded under the
 * rotate-on-use scheme (every login and every refresh inserts a row; rotation
 * only flips {@code revoked}). Lookups are by the unique {@code token_hash}, so
 * this is storage/cost hygiene rather than a latency concern. Scheduling is
 * enabled in {@link IdentitySupportConfiguration}.
 */
@Component
class RefreshTokenCleanup {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanup.class);

    private final RefreshTokenRepository refreshTokens;

    RefreshTokenCleanup(RefreshTokenRepository refreshTokens) {
        this.refreshTokens = refreshTokens;
    }

    /** Runs ~1 minute after startup, then daily. */
    @Scheduled(initialDelay = 60_000L, fixedDelay = 86_400_000L)
    @Transactional
    void purgeSpentTokens() {
        int removed = refreshTokens.deleteRevokedOrExpiredBefore(Instant.now());
        if (removed > 0) {
            log.info("Purged {} spent refresh token(s)", removed);
        }
    }
}
