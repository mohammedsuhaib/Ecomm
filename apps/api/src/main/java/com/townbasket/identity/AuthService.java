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

    /**
     * Admin: delivery agents for the roster/dispatch views. {@code includeInactive}
     * true returns the full roster (admin rider-management panel); false returns
     * only active agents (the order-assignment dropdown).
     */
    List<DeliveryAgentDto> listDeliveryAgents(boolean includeInactive);

    /**
     * Admin: onboard a delivery agent (email + password login, same as staff).
     *
     * @throws IllegalArgumentException if name/email/password are missing or the
     *     password is shorter than 8 characters (mapped to 400)
     * @throws com.townbasket.shared.BusinessRuleException if the email is already
     *     in use (mapped to 422)
     */
    DeliveryAgentDto createDeliveryAgent(CreateDeliveryAgentRequest request);

    /**
     * Admin: activate or deactivate a delivery agent. Deactivated agents can't log
     * in and drop out of the order-assignment dropdown.
     *
     * @throws com.townbasket.shared.ResourceNotFoundException if {@code agentId}
     *     doesn't refer to a delivery agent (mapped to 404)
     */
    DeliveryAgentDto setDeliveryAgentActive(Long agentId, boolean active);

    /**
     * True if {@code userId} is an existing, active user with the
     * {@code DELIVERY_AGENT} role. Used by the orders module to validate a
     * dispatch assignment before persisting it, so an order can't be assigned to
     * a non-existent / deactivated / non-agent id and silently fall out of every
     * agent's queue.
     */
    boolean isActiveDeliveryAgent(Long userId);

    /**
     * Active AND on duty — the test for handing a rider a NEW job. An off-duty
     * rider keeps the orders they already hold (they must finish them); they
     * just stop receiving more until they switch back on.
     */
    boolean isAvailableDeliveryAgent(Long userId);

    /** The calling rider's own availability. DELIVERY_AGENT only. */
    DutyStatusDto dutyStatus(Long agentId);

    /** The calling rider sets their own availability. DELIVERY_AGENT only. */
    DutyStatusDto setDutyStatus(Long agentId, boolean onDuty);

    /** Admin: every password-login account (STORE_STAFF and ADMIN), active or not. */
    List<StaffMemberDto> listStaff();

    /**
     * Set a new password on another account and sign out all its sessions.
     * This is the recovery path for a rider or staff member who has forgotten
     * theirs — there is no self-service reset because staff have no verified
     * email, so a human with authority does it in person.
     *
     * <p>Who may reset whom is decided from the CALLER's role as stored in the
     * database, not from the token: ADMIN → STORE_STAFF or DELIVERY_AGENT (or
     * another ADMIN); STORE_STAFF → DELIVERY_AGENT only. Nobody resets a
     * CUSTOMER (they have no password) or themselves (use change-password,
     * which proves the current one).
     *
     * @throws org.springframework.security.access.AccessDeniedException when the
     *         caller is not allowed to reset that target
     */
    void resetPassword(Long callerId, Long targetId, String newPassword);
}
