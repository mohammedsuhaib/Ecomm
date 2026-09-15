package com.townbasket.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.townbasket.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * An admin switching a rider off in the dashboard must actually put them out:
 * the next login is refused with a message that names the reason, the phone
 * that is already signed in cannot quietly carry on, and switching them back
 * on restores everything.
 */
class DeactivatedRiderLoginIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "Rider@12345";

    @Autowired
    AuthService authService;

    @Test
    void aDeactivatedRiderIsToldSoInsteadOfWrongPassword() {
        String email = "off-" + UUID.randomUUID().toString().substring(0, 8) + "@townbasket.local";
        Long rider = authService.createDeliveryAgent(
                new CreateDeliveryAgentRequest("Switched Off", email, PASSWORD)).id();
        AuthResponse before = authService.staffLogin(new StaffLoginRequest(email, PASSWORD));
        assertThat(before.user().role()).isEqualTo("DELIVERY_AGENT");

        authService.setDeliveryAgentActive(rider, false);

        // The right password gets the truth — a 403 with copy the app shows as is —
        // not the 401 that would send the rider off to reset a working password.
        assertThatThrownBy(() -> authService.staffLogin(new StaffLoginRequest(email, PASSWORD)))
                .isInstanceOf(AccountDeactivatedException.class)
                .hasMessageContaining("deactivated");

        // The WRONG password on the same account is still the generic 401: account
        // state is not something you can learn without holding the credential.
        assertThatThrownBy(() -> authService.staffLogin(new StaffLoginRequest(email, "not-it")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageNotContaining("deactivated");

        // The session that was open when the admin flipped the switch is dead too:
        // its refresh token was revoked, so the app lands on the login screen as
        // soon as the current access token runs out.
        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(before.refreshToken())))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void reactivatingRestoresLogin() {
        String email = "back-" + UUID.randomUUID().toString().substring(0, 8) + "@townbasket.local";
        Long rider = authService.createDeliveryAgent(
                new CreateDeliveryAgentRequest("Back Again", email, PASSWORD)).id();

        authService.setDeliveryAgentActive(rider, false);
        assertThatThrownBy(() -> authService.staffLogin(new StaffLoginRequest(email, PASSWORD)))
                .isInstanceOf(AccountDeactivatedException.class);

        authService.setDeliveryAgentActive(rider, true);
        AuthResponse after = authService.staffLogin(new StaffLoginRequest(email, PASSWORD));
        assertThat(after.accessToken()).isNotBlank();
        // ...and the fresh session refreshes normally; only the OLD family was revoked.
        assertThat(authService.refresh(new RefreshRequest(after.refreshToken())).accessToken()).isNotBlank();
    }
}
