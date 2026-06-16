package com.townbasket.identity;

/**
 * The result of validating an access token: the authenticated user's id and
 * role. Returned by {@link TokenService#authenticate(String)} and used by the
 * security filter to populate the {@code SecurityContext}.
 */
public record AuthenticatedUser(Long userId, Role role) {
}
