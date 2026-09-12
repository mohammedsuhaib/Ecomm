package com.townbasket.identity.internal;

import com.townbasket.identity.AddressInput;
import com.townbasket.identity.AuthResponse;
import com.townbasket.identity.AuthService;
import com.townbasket.identity.CreateDeliveryAgentRequest;
import com.townbasket.identity.DeliveryAgentDto;
import com.townbasket.identity.InvalidCredentialsException;
import com.townbasket.identity.LogoutRequest;
import com.townbasket.identity.PhoneVerifyRequest;
import com.townbasket.identity.RefreshRequest;
import com.townbasket.identity.Role;
import com.townbasket.identity.SavedAddressDto;
import com.townbasket.identity.StaffLoginRequest;
import com.townbasket.identity.TokenPair;
import com.townbasket.identity.UserDto;
import com.townbasket.shared.BusinessRuleException;
import com.townbasket.shared.ResourceNotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import com.townbasket.identity.StaffMemberDto;
import org.springframework.security.access.AccessDeniedException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Module-internal implementation of {@link AuthService}. Owns user upsert,
 * staff/customer login, refresh-token rotation/revocation, and saved-address
 * management.
 *
 * <p>Security posture: passwords are BCrypt-verified; refresh tokens are stored
 * only as SHA-256 hashes and rotate on use; failed logins surface a single
 * {@link InvalidCredentialsException} (mapped to 401) with no detail about which
 * factor failed. No tokens, hashes or passwords are ever logged.
 *
 * <p>Follow-up (documented, not implemented): rate-limiting on the OTP/login
 * endpoints. At ~100 orders/day there is no Redis; a simple per-phone/IP
 * in-memory or DB counter is the intended add-on if abuse appears.
 */
@Service
@Transactional
class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final UserRepository users;
    private final AddressRepository addresses;
    private final RefreshTokenRepository refreshTokens;
    private final RefreshTokenRevoker refreshTokenRevoker;
    private final JwtTokenService tokens;
    private final PhoneTokenVerifier phoneVerifier;
    private final PasswordEncoder passwordEncoder;

    AuthServiceImpl(UserRepository users,
                    AddressRepository addresses,
                    RefreshTokenRepository refreshTokens,
                    RefreshTokenRevoker refreshTokenRevoker,
                    JwtTokenService tokens,
                    PhoneTokenVerifier phoneVerifier,
                    PasswordEncoder passwordEncoder) {
        this.users = users;
        this.addresses = addresses;
        this.refreshTokens = refreshTokens;
        this.refreshTokenRevoker = refreshTokenRevoker;
        this.tokens = tokens;
        this.phoneVerifier = phoneVerifier;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public AuthResponse phoneVerify(PhoneVerifyRequest request) {
        if (request == null || request.firebaseIdToken() == null || request.firebaseIdToken().isBlank()) {
            throw new InvalidCredentialsException("firebaseIdToken is required");
        }
        PhoneTokenVerifier.VerifiedPhone verified = phoneVerifier.verify(request.firebaseIdToken());

        // Both verifiers converge here on one shape. Firebase returns E.164
        // (+919632500797) and the offline verifier returns bare digits; looking a
        // user up by the raw value made the same person two accounts when a
        // deployment switched verifiers, and stored a phone that then failed
        // checkout's 10-digit validation.
        String phone = IndianPhone.normalise(verified.phone());

        UserEntity user = users.findByPhone(phone)
                .map(existing -> {
                    if (existing.getFirebaseUid() == null) {
                        existing.setFirebaseUid(verified.firebaseUid());
                        existing.touch();
                    }
                    return existing;
                })
                .orElseGet(() -> users.saveAndFlush(
                        UserEntity.customer(phone, verified.firebaseUid())));

        return issueAuthResponse(user);
    }

    @Override
    public AuthResponse staffLogin(StaffLoginRequest request) {
        if (request == null || isBlank(request.email()) || isBlank(request.password())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }
        UserEntity user = users.findByEmail(request.email().trim().toLowerCase()).orElse(null);
        // Constant-ish work even when the user is missing isn't critical here; the
        // single error message already avoids enumeration.
        if (user == null
                || user.getRole() == Role.CUSTOMER
                || !user.isActive()
                || user.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }
        return issueAuthResponse(user);
    }

    @Override
    public TokenPair refresh(RefreshRequest request) {
        if (request == null || isBlank(request.refreshToken())) {
            throw new InvalidCredentialsException("Invalid refresh token");
        }
        String hash = tokens.hashRefreshToken(request.refreshToken());
        RefreshTokenEntity stored = refreshTokens.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid refresh token"));

        // Reuse detection: an already-revoked (rotated) token presented again is the
        // classic signal of a stolen/replayed token. Defensively revoke the whole
        // family (in its own committed transaction) and reject. Distinguished from a
        // merely expired token, which is just rejected.
        if (stored.isRevoked()) {
            refreshTokenRevoker.revokeFamily(stored.getUserId());
            throw new InvalidCredentialsException("Invalid refresh token");
        }
        if (!stored.isUsable(Instant.now())) {
            throw new InvalidCredentialsException("Invalid refresh token");
        }
        UserEntity user = users.findById(stored.getUserId())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid refresh token"));
        // A deactivated/suspended account must not be able to mint new access tokens
        // by riding its still-valid refresh token (staffLogin already blocks this on
        // the password path).
        if (!user.isActive()) {
            throw new InvalidCredentialsException("Invalid refresh token");
        }

        // Rotate: revoke the presented token, mint a fresh pair.
        stored.revoke();
        String access = tokens.issueAccessToken(user.getId(), user.getRole());
        String refresh = newStoredRefreshToken(user.getId());
        return new TokenPair(access, refresh);
    }

    @Override
    public void logout(LogoutRequest request) {
        if (request == null || isBlank(request.refreshToken())) {
            return; // Idempotent: nothing to revoke.
        }
        String hash = tokens.hashRefreshToken(request.refreshToken());
        refreshTokens.findByTokenHash(hash).ifPresent(RefreshTokenEntity::revoke);
    }

    @Override
    @Transactional(readOnly = true)
    public UserDto currentUser(Long userId) {
        return users.findById(userId)
                .map(AuthServiceImpl::toUserDto)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    @Override
    public UserDto updateProfile(Long userId, String name) {
        String trimmed = name == null ? null : name.trim();
        if (trimmed == null || trimmed.isEmpty() || trimmed.length() > 80) {
            throw new IllegalArgumentException("name is required and must be 1..80 characters");
        }
        UserEntity user = users.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        user.setName(trimmed);
        user.touch();
        return toUserDto(users.saveAndFlush(user));
    }

    @Override
    public void changePassword(Long userId, String current, String next) {
        UserEntity user = users.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        if (user.getPasswordHash() == null) {
            throw new BusinessRuleException("Password change is not available for this account");
        }
        if (isBlank(current) || !passwordEncoder.matches(current, user.getPasswordHash())) {
            throw new BusinessRuleException("Current password is incorrect");
        }
        if (next == null || next.length() < 8) {
            throw new IllegalArgumentException("newPassword must be at least 8 characters");
        }
        if (passwordEncoder.matches(next, user.getPasswordHash())) {
            throw new IllegalArgumentException("newPassword must be different from the current password");
        }
        user.setPasswordHash(passwordEncoder.encode(next));
        user.touch();
        users.saveAndFlush(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SavedAddressDto> listAddresses(Long userId) {
        return addresses.findByUserIdOrderByIsDefaultDescIdDesc(userId).stream()
                .map(AuthServiceImpl::toAddressDto)
                .toList();
    }

    @Override
    public SavedAddressDto addAddress(Long userId, AddressInput input) {
        validateAddress(input);
        boolean first = addresses.countByUserId(userId) == 0;
        boolean wantsDefault = Boolean.TRUE.equals(input.isDefault());
        boolean makeDefault = first || wantsDefault;
        if (makeDefault) {
            clearDefaults(userId);
        }
        AddressEntity saved = addresses.saveAndFlush(new AddressEntity(
                userId, input.label(), input.line(), input.lat(), input.lng(), makeDefault));
        return toAddressDto(saved);
    }

    @Override
    public SavedAddressDto updateAddress(Long userId, Long addressId, AddressInput input) {
        validateAddress(input);
        AddressEntity address = addresses.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found: " + addressId));
        address.setLabel(input.label());
        address.setLine(input.line());
        address.setLat(input.lat());
        address.setLng(input.lng());
        if (Boolean.TRUE.equals(input.isDefault()) && !address.isDefault()) {
            clearDefaults(userId);
            address.setDefault(true);
        }
        return toAddressDto(addresses.saveAndFlush(address));
    }

    @Override
    public void deleteAddress(Long userId, Long addressId) {
        AddressEntity address = addresses.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found: " + addressId));
        addresses.delete(address);
    }

    // --- helpers -----------------------------------------------------------

    private AuthResponse issueAuthResponse(UserEntity user) {
        String access = tokens.issueAccessToken(user.getId(), user.getRole());
        String refresh = newStoredRefreshToken(user.getId());
        return new AuthResponse(access, refresh, toUserDto(user));
    }

    /** Mint a refresh token, persist only its hash, return the raw value. */
    private String newStoredRefreshToken(Long userId) {
        String raw = tokens.newRefreshToken();
        refreshTokens.save(new RefreshTokenEntity(
                userId, tokens.hashRefreshToken(raw), tokens.refreshExpiry()));
        return raw;
    }

    private void clearDefaults(Long userId) {
        for (AddressEntity a : addresses.findByUserIdOrderByIsDefaultDescIdDesc(userId)) {
            if (a.isDefault()) {
                a.setDefault(false);
            }
        }
    }

    private static void validateAddress(AddressInput input) {
        if (input == null || isBlank(input.line())) {
            throw new IllegalArgumentException("address line is required");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeliveryAgentDto> listDeliveryAgents(boolean includeInactive) {
        List<UserEntity> agents = includeInactive
                ? users.findByRoleOrderByNameAsc(Role.DELIVERY_AGENT)
                : users.findByRoleAndActiveTrueOrderByNameAsc(Role.DELIVERY_AGENT);
        return agents.stream().map(AuthServiceImpl::toDeliveryAgentDto).toList();
    }

    @Override
    public DeliveryAgentDto createDeliveryAgent(CreateDeliveryAgentRequest request) {
        if (request == null || isBlank(request.name())) {
            throw new IllegalArgumentException("name is required");
        }
        if (isBlank(request.email()) || !request.email().contains("@")) {
            throw new IllegalArgumentException("a valid email is required");
        }
        if (request.password() == null || request.password().length() < 8) {
            throw new IllegalArgumentException("password must be at least 8 characters");
        }
        String email = request.email().trim().toLowerCase();
        if (users.existsByEmail(email)) {
            throw new BusinessRuleException("That email is already in use");
        }
        UserEntity agent = UserEntity.deliveryAgent(
                request.name().trim(), email, passwordEncoder.encode(request.password()));
        return toDeliveryAgentDto(users.saveAndFlush(agent));
    }

    @Override
    public DeliveryAgentDto setDeliveryAgentActive(Long agentId, boolean active) {
        UserEntity agent = users.findById(agentId)
                .filter(u -> u.getRole() == Role.DELIVERY_AGENT)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery agent not found: " + agentId));
        agent.setActive(active);
        agent.touch();
        return toDeliveryAgentDto(users.saveAndFlush(agent));
    }

    @Override
    @Transactional(readOnly = true)
    public List<StaffMemberDto> listStaff() {
        List<UserEntity> staff = new ArrayList<>(users.findByRoleOrderByNameAsc(Role.ADMIN));
        staff.addAll(users.findByRoleOrderByNameAsc(Role.STORE_STAFF));
        return staff.stream()
                .map(u -> new StaffMemberDto(u.getId(), u.getName(), u.getEmail(), u.getRole().name(), u.isActive()))
                .toList();
    }

    @Override
    public void resetPassword(Long callerId, Long targetId, String newPassword) {
        UserEntity caller = users.findById(callerId)
                .orElseThrow(() -> new AccessDeniedException("Unknown caller"));
        UserEntity target = users.findById(targetId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + targetId));

        if (caller.getId().equals(target.getId())) {
            throw new BusinessRuleException("Use change-password for your own account");
        }
        if (target.getPasswordHash() == null || target.getRole() == Role.CUSTOMER) {
            throw new BusinessRuleException("This account has no password to reset");
        }
        boolean allowed = switch (caller.getRole()) {
            case ADMIN -> true; // any password-login account except themselves
            case STORE_STAFF -> target.getRole() == Role.DELIVERY_AGENT;
            default -> false;
        };
        if (!allowed) {
            throw new AccessDeniedException("You may not reset that account's password");
        }
        if (newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException("newPassword must be at least 8 characters");
        }

        target.setPasswordHash(passwordEncoder.encode(newPassword));
        target.touch();
        users.saveAndFlush(target);
        // Whoever held the old password must not keep a live session: their
        // refresh tokens die now, and access tokens run out within 15 minutes.
        refreshTokenRevoker.revokeFamily(target.getId());
        log.info("Password reset for user {} ({}) by {} ({})",
                target.getId(), target.getRole(), caller.getId(), caller.getRole());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isActiveDeliveryAgent(Long userId) {
        return userId != null
                && users.existsByIdAndRoleAndActiveTrue(userId, Role.DELIVERY_AGENT);
    }

    private static UserDto toUserDto(UserEntity u) {
        return new UserDto(u.getId(), u.getRole().name(), u.getName(), u.getPhone(), u.getEmail());
    }

    private static DeliveryAgentDto toDeliveryAgentDto(UserEntity u) {
        return new DeliveryAgentDto(u.getId(), u.getName(), u.getEmail(), u.isActive());
    }

    private static SavedAddressDto toAddressDto(AddressEntity a) {
        return new SavedAddressDto(a.getId(), a.getLabel(), a.getLine(), a.getLat(), a.getLng(), a.isDefault());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
