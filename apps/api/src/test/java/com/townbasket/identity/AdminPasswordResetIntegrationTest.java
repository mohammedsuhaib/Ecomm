package com.townbasket.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.townbasket.AbstractIntegrationTest;
import com.townbasket.shared.BusinessRuleException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;

/**
 * Admin password reset: the recovery path for a rider or staff member who has
 * forgotten theirs. Covers the effect (new password works, old sessions die)
 * and the who-may-reset-whom matrix, decided from the caller's stored role.
 */
class AdminPasswordResetIntegrationTest extends AbstractIntegrationTest {

    // Seeded accounts (V2_2): admin + store staff.
    private static final String ADMIN_EMAIL = "admin@townbasket.local";
    private static final String STAFF_EMAIL = "staff@townbasket.local";

    @Autowired
    AuthService authService;

    private Long idOf(String email, String password) {
        return authService.staffLogin(new StaffLoginRequest(email, password)).user().id();
    }

    private DeliveryAgentDto newRider() {
        String slug = "reset-" + UUID.randomUUID().toString().substring(0, 8);
        return authService.createDeliveryAgent(new CreateDeliveryAgentRequest(
                "Rider " + slug, slug + "@townbasket.local", "oldpassword1"));
    }

    @Test
    void adminResetsARiderAndTheirOldSessionsStopWorking() {
        Long adminId = idOf(ADMIN_EMAIL, "Admin@12345");
        DeliveryAgentDto rider = newRider();
        AuthResponse riderSession = authService.staffLogin(new StaffLoginRequest(rider.email(), "oldpassword1"));

        authService.resetPassword(adminId, rider.id(), "newpassword2");

        // New password works, old one does not.
        assertThat(authService.staffLogin(new StaffLoginRequest(rider.email(), "newpassword2")).user().id())
                .isEqualTo(rider.id());
        assertThatThrownBy(() -> authService.staffLogin(new StaffLoginRequest(rider.email(), "oldpassword1")))
                .isInstanceOf(InvalidCredentialsException.class);
        // The session issued under the old password cannot refresh any more.
        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(riderSession.refreshToken())))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void storeStaffMayResetRidersButNotStaff() {
        Long staffId = idOf(STAFF_EMAIL, "Staff@12345");
        Long adminId = idOf(ADMIN_EMAIL, "Admin@12345");
        DeliveryAgentDto rider = newRider();

        authService.resetPassword(staffId, rider.id(), "resetbystaff1");
        assertThat(authService.staffLogin(new StaffLoginRequest(rider.email(), "resetbystaff1")).user().id())
                .isEqualTo(rider.id());

        assertThatThrownBy(() -> authService.resetPassword(staffId, adminId, "takeover123"))
                .as("staff must not be able to reset an admin")
                .isInstanceOf(AccessDeniedException.class);
        // Admin still logs in with the seeded password.
        assertThat(authService.staffLogin(new StaffLoginRequest(ADMIN_EMAIL, "Admin@12345")).user().id())
                .isEqualTo(adminId);
    }

    @Test
    void nobodyResetsThemselvesACustomerOrToAShortPassword() {
        Long adminId = idOf(ADMIN_EMAIL, "Admin@12345");
        Long customerId = authService.phoneVerify(new PhoneVerifyRequest("dev:9777700099")).user().id();
        DeliveryAgentDto rider = newRider();

        assertThatThrownBy(() -> authService.resetPassword(adminId, adminId, "selfreset123"))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> authService.resetPassword(adminId, customerId, "customer123"))
                .as("customers log in by OTP; there is no password to reset")
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> authService.resetPassword(adminId, rider.id(), "short"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void staffListingShowsAdminsAndStaffOnly() {
        newRider();
        assertThat(authService.listStaff())
                .isNotEmpty()
                .allSatisfy(m -> assertThat(m.role()).isIn("ADMIN", "STORE_STAFF"))
                .anySatisfy(m -> assertThat(m.email()).isEqualTo(ADMIN_EMAIL));
    }
}
