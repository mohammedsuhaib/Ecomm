package com.townbasket.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.townbasket.AbstractIntegrationTest;
import com.townbasket.shared.TooManyRequestsException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

/**
 * The per-ACCOUNT staff-login failure throttle, which is what bounds password
 * guessing now that the per-IP auth limit is sized for a crowd sharing one
 * public address (NAT/CGNAT).
 *
 * <p>Drives {@link AuthService} directly — service calls, never HTTP — so the
 * per-IP edge filter plays no part and these assertions are about the
 * per-account budget alone. The budget is read from configuration
 * ({@code townbasket.security.login-throttle.max-failures}, pinned low for
 * tests) rather than hard-coded.
 */
class StaffLoginThrottleIntegrationTest extends AbstractIntegrationTest {

    /** Seeded admin (see the identity seed migration). */
    private static final String ADMIN_EMAIL = "admin@townbasket.local";
    private static final String ADMIN_PASSWORD = "Admin@12345";
    private static final String WRONG_PASSWORD = "definitely-not-the-password";

    @Autowired
    AuthService authService;

    @Value("${townbasket.security.login-throttle.max-failures}")
    int maxFailures;

    @Test
    void failedAttemptsBeyondTheBudgetAreRefusedWith429() {
        // An address with no account at all: unknown emails are counted too (that
        // is what bounds account enumeration), and the refusal is identical to a
        // known account's, so it still leaks nothing.
        String email = "no-such-staff-" + System.nanoTime() + "@townbasket.local";

        for (int i = 0; i < maxFailures; i++) {
            assertThatThrownBy(() -> authService.staffLogin(new StaffLoginRequest(email, WRONG_PASSWORD)))
                    .as("attempt %d should still be a plain 401", i + 1)
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        Throwable refused = catchThrowable(
                () -> authService.staffLogin(new StaffLoginRequest(email, WRONG_PASSWORD)));

        assertThat(refused)
                .as("attempt %d should be throttled, not merely rejected", maxFailures + 1)
                .isInstanceOf(TooManyRequestsException.class);
        // Carries the wait, which GlobalExceptionHandler turns into Retry-After.
        assertThat(((TooManyRequestsException) refused).retryAfterSeconds()).isPositive();
    }

    @Test
    void theEmailIsNormalisedSoCaseCannotBuyAFreshBudget() {
        String email = "no-such-staff-" + System.nanoTime() + "@townbasket.local";

        for (int i = 0; i < maxFailures; i++) {
            assertThatThrownBy(() -> authService.staffLogin(new StaffLoginRequest(email, WRONG_PASSWORD)))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        // Same account, shouted: must hit the same bucket, not a new one.
        assertThatThrownBy(() -> authService.staffLogin(
                new StaffLoginRequest("  " + email.toUpperCase() + "  ", WRONG_PASSWORD)))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void aCorrectPasswordClearsTheAccountsFailures() {
        // Stay one short of the budget, so the account is not yet locked...
        for (int i = 0; i < maxFailures - 1; i++) {
            assertThatThrownBy(() -> authService.staffLogin(
                    new StaffLoginRequest(ADMIN_EMAIL, WRONG_PASSWORD)))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        assertThat(authService.staffLogin(new StaffLoginRequest(ADMIN_EMAIL, ADMIN_PASSWORD)).accessToken())
                .isNotBlank();

        // ...and the successful login must have reset the count: the same number of
        // fumbles again is still a 401, not a lockout. This is what keeps staff (and
        // every other test class logging in as this admin) out of the limiter's way.
        for (int i = 0; i < maxFailures - 1; i++) {
            assertThatThrownBy(() -> authService.staffLogin(
                    new StaffLoginRequest(ADMIN_EMAIL, WRONG_PASSWORD)))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        // Leave the shared account's bucket empty for whichever class runs next.
        assertThat(authService.staffLogin(new StaffLoginRequest(ADMIN_EMAIL, ADMIN_PASSWORD)).accessToken())
                .isNotBlank();
    }
}
