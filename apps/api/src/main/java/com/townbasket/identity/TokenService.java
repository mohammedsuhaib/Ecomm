package com.townbasket.identity;

import java.util.Optional;

/**
 * Published API for validating our own access tokens. The root security filter
 * depends only on this interface (and {@link AuthenticatedUser} / {@link Role}),
 * never on the identity module's internals.
 */
public interface TokenService {

    /**
     * Validate an HS256 access token (signature, expiry, {@code typ=access}) and
     * return the authenticated user. Empty if the token is missing, malformed,
     * expired, or otherwise invalid. Never throws on a bad token.
     */
    Optional<AuthenticatedUser> authenticate(String accessToken);
}
