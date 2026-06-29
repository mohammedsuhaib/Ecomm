package com.townbasket.identity;

import java.util.List;

/**
 * Published API of the identity module: customer phone login, staff login,
 * token rotation/logout, profile and saved-address management.
 *
 * <p>All address operations are scoped to the supplied {@code userId} (the
 * caller's id, resolved by the security layer from a valid access token).
 */
public interface AuthService {

    /**
     * Customer login/signup from a Firebase ID token (dev: {@code dev:<phone>}).
     * Verifies via the {@code PhoneTokenVerifier} port, upserts a CUSTOMER by
     * phone, issues a fresh token pair.
     *
     * @throws InvalidCredentialsException if the token is invalid/expired
     */
    AuthResponse phoneVerify(PhoneVerifyRequest request);

    /**
     * Staff/admin login by email + password (BCrypt-verified).
     *
     * @throws InvalidCredentialsException if the credentials don't match
     */
    AuthResponse staffLogin(StaffLoginRequest request);

    /**
     * Rotate a refresh token: validate (not expired, not revoked), revoke the
     * presented token, issue a new access+refresh pair.
     *
     * @throws InvalidCredentialsException if the refresh token is invalid
     */
    TokenPair refresh(RefreshRequest request);

    /** Revoke a refresh token. Idempotent: unknown/already-revoked tokens are a no-op. */
    void logout(LogoutRequest request);

    /** The current user's profile. */
    UserDto currentUser(Long userId);

    /**
     * Update the caller's display name. {@code name} is trimmed and must be
     * 1..80 characters; persists with {@code saveAndFlush} and returns the
     * refreshed profile.
     *
     * @throws IllegalArgumentException if {@code name} is blank or longer than 80
     *     characters after trimming (mapped to 400)
     */
    UserDto updateProfile(Long userId, String name);

    /**
     * Change the caller's password. Only accounts that HAVE a password
     * (STORE_STAFF / ADMIN) are eligible; the {@code current} password is
     * BCrypt-verified and the {@code next} password is encoded and stored with
     * {@code saveAndFlush}.
     *
     * @throws com.townbasket.shared.BusinessRuleException if the account has no
     *     password (mapped to 422) or {@code current} is incorrect (422)
     * @throws IllegalArgumentException if {@code next} is shorter than 8
     *     characters or equal to {@code current} (mapped to 400)
     */
    void changePassword(Long userId, String current, String next);

    /** The user's saved addresses, default first then newest. */
    List<SavedAddressDto> listAddresses(Long userId);

    /** Add a saved address (the first address for a user becomes the default). */
    SavedAddressDto addAddress(Long userId, AddressInput input);

    /**
     * Update one of the user's addresses.
     *
     * @throws com.townbasket.shared.ResourceNotFoundException if not found / not owned
     */
    SavedAddressDto updateAddress(Long userId, Long addressId, AddressInput input);

    /**
     * Delete one of the user's addresses.
     *
     * @throws com.townbasket.shared.ResourceNotFoundException if not found / not owned
     */
    void deleteAddress(Long userId, Long addressId);

    /** Admin: all active delivery agents (for dispatching/assigning orders). */
    List<UserDto> listDeliveryAgents();
}
