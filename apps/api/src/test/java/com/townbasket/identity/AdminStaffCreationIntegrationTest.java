package com.townbasket.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.townbasket.AbstractIntegrationTest;
import com.townbasket.shared.BusinessRuleException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Creating STORE_STAFF and ADMIN accounts. Before this endpoint the only ADMIN
 * the app had was the dev seed (V2_2), and adding a manager meant an INSERT
 * against the production database — so the behaviour worth pinning down is that
 * a created account can actually log in, and that the role boundary holds.
 */
class AdminStaffCreationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    AuthService authService;

    /** A unique-per-run email so repeated runs don't collide on the UNIQUE index. */
    private static String freshEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@townbasket.local";
    }

    @Test
    void createdStaffCanLogInAndAppearsInTheDirectory() {
        String email = freshEmail("staff");

        StaffMemberDto created = authService.createStaff(
                new CreateStaffRequest("Asha Manager", email, "goodpassword1", "STORE_STAFF"));

        assertThat(created.id()).isNotNull();
        assertThat(created.email()).isEqualTo(email);
        assertThat(created.role()).isEqualTo("STORE_STAFF");
        assertThat(created.active()).isTrue();

        // The point of the endpoint: the account is usable, not merely stored.
        assertThat(authService.staffLogin(new StaffLoginRequest(email, "goodpassword1")).user().id())
                .isEqualTo(created.id());
        assertThat(authService.listStaff()).extracting(StaffMemberDto::email).contains(email);
    }

    @Test
    void anAdminCreatedThisWayCanItselfCreateStaff() {
        // Authority propagates deliberately — this is the bootstrap path out of
        // the seeded dev admin, so a real admin must be able to build the team.
        String adminEmail = freshEmail("admin");
        StaffMemberDto admin = authService.createStaff(
                new CreateStaffRequest("Ravi Owner", adminEmail, "ownerpassword1", "ADMIN"));
        assertThat(admin.role()).isEqualTo("ADMIN");
        assertThat(authService.staffLogin(new StaffLoginRequest(adminEmail, "ownerpassword1")).user().id())
                .isEqualTo(admin.id());

        String staffEmail = freshEmail("staff");
        assertThat(authService.createStaff(
                new CreateStaffRequest("Team Member", staffEmail, "teampassword1", "STORE_STAFF")).id())
                .isNotNull();
    }

    @Test
    void roleIsCaseInsensitiveButRidersAndCustomersAreRefused() {
        assertThat(authService.createStaff(new CreateStaffRequest(
                "Lower Case", freshEmail("lower"), "goodpassword1", "store_staff")).role())
                .isEqualTo("STORE_STAFF");

        // Riders have an on-duty lifecycle this path does not set up, so they
        // are sent to their own endpoint rather than silently created here.
        assertThatThrownBy(() -> authService.createStaff(new CreateStaffRequest(
                "Rider", freshEmail("rider"), "goodpassword1", "DELIVERY_AGENT")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("delivery-agents");

        // A customer with a password is a shape nothing else in the module
        // produces; it would be a password login that bypasses phone OTP.
        assertThatThrownBy(() -> authService.createStaff(new CreateStaffRequest(
                "Customer", freshEmail("customer"), "goodpassword1", "CUSTOMER")))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> authService.createStaff(new CreateStaffRequest(
                "Nonsense", freshEmail("nonsense"), "goodpassword1", "SUPERUSER")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void badInputIsRejectedBeforeAnAccountExists() {
        assertThatThrownBy(() -> authService.createStaff(
                new CreateStaffRequest(" ", freshEmail("blank"), "goodpassword1", "ADMIN")))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> authService.createStaff(
                new CreateStaffRequest("No At Sign", "not-an-email", "goodpassword1", "ADMIN")))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> authService.createStaff(
                new CreateStaffRequest("Short Password", freshEmail("short"), "seven77", "ADMIN")))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> authService.createStaff(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void duplicateEmailIsRefusedAcrossEveryAccountKind() {
        String email = freshEmail("dupe");
        authService.createStaff(new CreateStaffRequest("First", email, "goodpassword1", "STORE_STAFF"));

        assertThatThrownBy(() -> authService.createStaff(
                new CreateStaffRequest("Second", email, "goodpassword1", "ADMIN")))
                .isInstanceOf(BusinessRuleException.class);

        // Uniqueness spans roles — the email column is unique for the whole
        // table, so a rider's address is not available to a staff member.
        String riderEmail = freshEmail("rider");
        authService.createDeliveryAgent(new CreateDeliveryAgentRequest("Rider", riderEmail, "goodpassword1"));
        assertThatThrownBy(() -> authService.createStaff(
                new CreateStaffRequest("Clash", riderEmail, "goodpassword1", "STORE_STAFF")))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void emailIsNormalisedSoCaseCannotCreateADuplicate() {
        String email = freshEmail("Case");
        StaffMemberDto created = authService.createStaff(
                new CreateStaffRequest("Mixed Case", "  " + email.toUpperCase() + "  ",
                        "goodpassword1", "ADMIN"));

        assertThat(created.email()).isEqualTo(email.toLowerCase());
        assertThatThrownBy(() -> authService.createStaff(
                new CreateStaffRequest("Same Again", email.toLowerCase(), "goodpassword1", "ADMIN")))
                .isInstanceOf(BusinessRuleException.class);
    }
}
