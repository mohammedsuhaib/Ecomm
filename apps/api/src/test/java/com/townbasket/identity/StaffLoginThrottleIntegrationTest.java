package com.townbasket.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.townbasket.AbstractIntegrationTest;
import com.townbasket.shared.TooManyRequestsException;
import java.util.UUID;
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
 *
 * <p><strong>Every account here is created by the test that uses it.</strong>
 * The limiter is a singleton in the Spring context that all
 * {@link AbstractIntegrationTest} classes share, and its state is keyed by
 * email and outlives a test class, so filling the budget of a SEEDED account
 * (admin@townbasket.local) locks it for every later class that signs in as it —
 * an earlier version of this test did exactly that and took three
 * {@code AdminPasswordResetIntegrationTest} cases down with it, since a correct
 * password on a throttled account is refused too. Fresh per-test accounts have
 * no such reach: nothing else knows their addresses. For the same reason no
 * assertion here may depend on a shared account's failure count, which any
 * other class is free to change.
 */
class StaffLoginThrottleIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "Rider@12345";
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
        // This account's own rider, so filling and emptying its budget cannot
        // reach any other test — see the class note.
        String email = newRider();

        // Stay one short of the budget, so the account is not yet locked...
        fumble(email, maxFailures - 1);

        assertThat(authService.staffLogin(new StaffLoginRequest(email, PASSWORD)).accessToken())
                .as("still under the budget, so the right password works")
                .isNotBlank();

        // ...and the successful login must have reset the count: the same number of
        // fumbles again is still a 401, not a lockout. This is what keeps staff who
        // mistype, then get it right, then mistype again out of the limiter's way.
        fumble(email, maxFailures - 1);

        assertThat(authService.staffLogin(new StaffLoginRequest(email, PASSWORD)).accessToken())
                .as("the count restarted, so the budget was not exhausted")
                .isNotBlank();
    }

    // ---- helpers -----------------------------------------------------------

    /** A rider nobody else knows about, with {@link #PASSWORD}. Returns its email. */
    private String newRider() {
        String slug = "throttle-" + UUID.randomUUID().toString().substring(0, 8);
        return authService.createDeliveryAgent(new CreateDeliveryAgentRequest(
                "Rider " + slug, slug + "@townbasket.local", PASSWORD)).email();
    }

    /** {@code times} wrong passwords, each of which must still be a plain 401. */
    private void fumble(String email, int times) {
        for (int i = 0; i < times; i++) {
            assertThatThrownBy(() -> authService.staffLogin(new StaffLoginRequest(email, WRONG_PASSWORD)))
                    .as("fumble %d of %d should be a plain 401, not a lockout", i + 1, times)
                    .isInstanceOf(InvalidCredentialsException.class);
        }
    }
}
