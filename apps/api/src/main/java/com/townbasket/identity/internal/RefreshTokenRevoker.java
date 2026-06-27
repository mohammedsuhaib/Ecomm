package com.townbasket.identity.internal;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Revokes a user's entire refresh-token family in its OWN transaction.
 *
 * <p>Used by the refresh-token reuse-detection path: when an already-revoked
 * (rotated) token is presented again — the classic signal of a stolen/replayed
 * token — the whole family must be revoked AND the request rejected. Because the
 * caller rejects by throwing (which rolls back its transaction), the revoke is
 * performed here in a {@code REQUIRES_NEW} transaction that commits
 * independently, so the defensive revocation survives the caller's rollback.
 */
@Component
class RefreshTokenRevoker {

    private final RefreshTokenRepository refreshTokens;

    RefreshTokenRevoker(RefreshTokenRepository refreshTokens) {
        this.refreshTokens = refreshTokens;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void revokeFamily(Long userId) {
        refreshTokens.revokeAllByUserId(userId);
    }
}
