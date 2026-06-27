package com.townbasket.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.townbasket.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Service-level integration test for the identity module against a real Postgres
 * (Testcontainers). Exercises phone verify (CUSTOMER upsert + tokens), profile,
 * staff login + role gating, refresh rotation/reuse-rejection, logout
 * revocation, and the address CRUD single-default rule.
 */
class IdentityIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    AuthService authService;
    @Autowired
    TokenService tokenService;
    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void phoneVerifyCreatesCustomerAndIssuesUsableTokens() {
        AuthResponse res = authService.phoneVerify(new PhoneVerifyRequest("dev:9999900000"));

        assertThat(res.accessToken()).isNotBlank();
        assertThat(res.refreshToken()).isNotBlank();
        assertThat(res.user().role()).isEqualTo("CUSTOMER");
        assertThat(res.user().phone()).isEqualTo("9999900000");

        // Access token authenticates to the same user + role.
        AuthenticatedUser authed = tokenService.authenticate(res.accessToken()).orElseThrow();
        assertThat(authed.userId()).isEqualTo(res.user().id());
        assertThat(authed.role()).isEqualTo(Role.CUSTOMER);

        // currentUser returns the profile.
        UserDto me = authService.currentUser(res.user().id());
        assertThat(me.id()).isEqualTo(res.user().id());

        // Re-verifying the same phone returns the SAME user (upsert, not duplicate).
        AuthResponse again = authService.phoneVerify(new PhoneVerifyRequest("dev:9999900000"));
        assertThat(again.user().id()).isEqualTo(res.user().id());
    }

    @Test
    void fakeVerifierRejectsNonDevTokens() {
        assertThatThrownBy(() -> authService.phoneVerify(new PhoneVerifyRequest("not-a-dev-token")))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() -> authService.phoneVerify(new PhoneVerifyRequest("dev:12345")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void staffLoginWorksForSeededAdminAndIsRoleGated() {
        AuthResponse admin = authService.staffLogin(new StaffLoginRequest("admin@townbasket.local", "Admin@12345"));
        assertThat(admin.user().role()).isEqualTo("ADMIN");
        assertThat(tokenService.authenticate(admin.accessToken()).orElseThrow().role()).isEqualTo(Role.ADMIN);

        AuthResponse staff = authService.staffLogin(new StaffLoginRequest("staff@townbasket.local", "Staff@12345"));
        assertThat(staff.user().role()).isEqualTo("STORE_STAFF");

        // Wrong password rejected.
        assertThatThrownBy(() -> authService.staffLogin(
                new StaffLoginRequest("admin@townbasket.local", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void customerCannotUseStaffLogin() {
        // A phone customer has no email/password, so staff login can't match them.
        authService.phoneVerify(new PhoneVerifyRequest("dev:9999900001"));
        assertThatThrownBy(() -> authService.staffLogin(
                new StaffLoginRequest("9999900001", "anything")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void refreshRotatesAndOldTokenIsRejectedAfterUse() {
        AuthResponse res = authService.phoneVerify(new PhoneVerifyRequest("dev:9999900002"));
        String firstRefresh = res.refreshToken();

        TokenPair rotated = authService.refresh(new RefreshRequest(firstRefresh));
        assertThat(rotated.accessToken()).isNotBlank();
        assertThat(rotated.refreshToken()).isNotEqualTo(firstRefresh);

        // The used refresh token is revoked -> reuse rejected.
        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(firstRefresh)))
                .isInstanceOf(InvalidCredentialsException.class);

        // The new refresh token works.
        TokenPair rotatedAgain = authService.refresh(new RefreshRequest(rotated.refreshToken()));
        assertThat(rotatedAgain.refreshToken()).isNotBlank();
    }

    @Test
    void reuseOfRevokedTokenRevokesWholeFamily() {
        AuthResponse res = authService.phoneVerify(new PhoneVerifyRequest("dev:9999900005"));
        TokenPair rotated = authService.refresh(new RefreshRequest(res.refreshToken()));

        // Replaying the already-rotated (revoked) token is treated as theft: the
        // request is rejected AND the whole token family is revoked (in its own
        // committed transaction, so it survives the rejecting rollback)...
        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(res.refreshToken())))
                .isInstanceOf(InvalidCredentialsException.class);

        // ...so even the freshly-minted token from the legitimate rotation is now dead.
        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(rotated.refreshToken())))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void deactivatedUserCannotRefresh() {
        AuthResponse res = authService.phoneVerify(new PhoneVerifyRequest("dev:9999900006"));
        // Deactivate the account out-of-band (e.g. an admin suspends a staff member).
        jdbcTemplate.update("UPDATE identity.users SET active = false WHERE id = ?", res.user().id());

        // A still-valid refresh token must not be able to mint new access tokens.
        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(res.refreshToken())))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void logoutRevokesRefreshTokenAndIsIdempotent() {
        AuthResponse res = authService.phoneVerify(new PhoneVerifyRequest("dev:9999900003"));

        authService.logout(new LogoutRequest(res.refreshToken()));
        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(res.refreshToken())))
                .isInstanceOf(InvalidCredentialsException.class);

        // Idempotent: logging out again (or an unknown token) is a no-op, no throw.
        authService.logout(new LogoutRequest(res.refreshToken()));
        authService.logout(new LogoutRequest("never-issued"));
    }

    @Test
    void addressCrudEnforcesSingleDefault() {
        Long userId = authService.phoneVerify(new PhoneVerifyRequest("dev:9999900004")).user().id();

        // First address auto-defaults.
        SavedAddressDto home = authService.addAddress(userId,
                new AddressInput("Home", "12 MG Road", 12.21, 76.89, null));
        assertThat(home.isDefault()).isTrue();

        // Second, non-default.
        SavedAddressDto work = authService.addAddress(userId,
                new AddressInput("Work", "5 Residency Rd", 12.22, 76.88, false));
        assertThat(work.isDefault()).isFalse();

        // Promote work to default -> home is un-defaulted (single default).
        SavedAddressDto promoted = authService.updateAddress(userId, work.id(),
                new AddressInput("Work", "5 Residency Rd", 12.22, 76.88, true));
        assertThat(promoted.isDefault()).isTrue();

        List<SavedAddressDto> all = authService.listAddresses(userId);
        assertThat(all).hasSize(2);
        assertThat(all.get(0).isDefault()).isTrue(); // default first
        assertThat(all.stream().filter(SavedAddressDto::isDefault).count()).isEqualTo(1);

        // Delete; 404-style not found for a foreign id.
        authService.deleteAddress(userId, home.id());
        assertThat(authService.listAddresses(userId)).hasSize(1);
        assertThatThrownBy(() -> authService.deleteAddress(userId, 999_999L))
                .isInstanceOf(com.townbasket.shared.ResourceNotFoundException.class);
    }
}
