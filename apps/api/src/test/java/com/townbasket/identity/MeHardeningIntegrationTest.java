package com.townbasket.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.townbasket.shared.ApiError;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * End-to-end HTTP test (RANDOM_PORT + TestRestTemplate) for the M4-hardening
 * "me" endpoints and the auth rate limiter. Boots against a real Postgres (same
 * singleton container pattern as the other live-port tests).
 *
 * <p>Covers: {@code PUT /me} name update (happy + 400 invalid),
 * {@code POST /me/password} (204 happy, 422 wrong-current, 422 customer-forbidden,
 * 400 weak-new), and the auth rate limiter (429 after the configured threshold).
 *
 * <p>Where a precondition needs an authed session, tokens are minted via the
 * {@code AuthService} directly (a service call, NOT an HTTP call), so those
 * preconditions don't consume the per-IP auth rate-limit budget.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MeHardeningIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("townbasket")
                    .withUsername("townbasket")
                    .withPassword("townbasket");

    static {
        POSTGRES.start();
    }

    @Autowired
    TestRestTemplate rest;
    @Autowired
    AuthService authService;

    // --- Change 2: PUT /me (update display name) --------------------------

    @Test
    void updateNameHappyPathReturnsUpdatedUser() {
        String token = authService.phoneVerify(new PhoneVerifyRequest("dev:9777700000")).accessToken();

        ResponseEntity<UserDto> res = rest.exchange(
                "/api/v1/me", HttpMethod.PUT,
                jsonBearer(token, new UpdateProfileRequest("  Asha Rao  ")), UserDto.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().name()).isEqualTo("Asha Rao"); // trimmed
    }

    @Test
    void updateNameRejectsBlankWith400() {
        String token = authService.phoneVerify(new PhoneVerifyRequest("dev:9777700001")).accessToken();

        ResponseEntity<ApiError> res = rest.exchange(
                "/api/v1/me", HttpMethod.PUT,
                jsonBearer(token, new UpdateProfileRequest("   ")), ApiError.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateNameRejectsTooLongWith400() {
        String token = authService.phoneVerify(new PhoneVerifyRequest("dev:9777700002")).accessToken();

        ResponseEntity<ApiError> res = rest.exchange(
                "/api/v1/me", HttpMethod.PUT,
                jsonBearer(token, new UpdateProfileRequest("x".repeat(81))), ApiError.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // --- Change 3: POST /me/password (staff/admin password change) --------

    @Test
    void changePasswordHappyPathReturns204AndNewPasswordWorks() {
        // Seeded staff account (see identity seed migration).
        String token = authService.staffLogin(
                new StaffLoginRequest("staff@townbasket.local", "Staff@12345")).accessToken();

        ResponseEntity<Void> res = rest.exchange(
                "/api/v1/me/password", HttpMethod.POST,
                jsonBearer(token, new ChangePasswordRequest("Staff@12345", "NewStaff@12345")), Void.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // New password is usable; restore the original so the test is repeatable.
        String token2 = authService.staffLogin(
                new StaffLoginRequest("staff@townbasket.local", "NewStaff@12345")).accessToken();
        ResponseEntity<Void> restore = rest.exchange(
                "/api/v1/me/password", HttpMethod.POST,
                jsonBearer(token2, new ChangePasswordRequest("NewStaff@12345", "Staff@12345")), Void.class);
        assertThat(restore.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void changePasswordWrongCurrentReturns422() {
        String token = authService.staffLogin(
                new StaffLoginRequest("admin@townbasket.local", "Admin@12345")).accessToken();

        ResponseEntity<ApiError> res = rest.exchange(
                "/api/v1/me/password", HttpMethod.POST,
                jsonBearer(token, new ChangePasswordRequest("not-the-password", "Whatever@123")), ApiError.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void changePasswordForCustomerReturns422() {
        String token = authService.phoneVerify(new PhoneVerifyRequest("dev:9777700003")).accessToken();

        ResponseEntity<ApiError> res = rest.exchange(
                "/api/v1/me/password", HttpMethod.POST,
                jsonBearer(token, new ChangePasswordRequest("anything", "Strong@12345")), ApiError.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void changePasswordWeakNewReturns400() {
        String token = authService.staffLogin(
                new StaffLoginRequest("admin@townbasket.local", "Admin@12345")).accessToken();

        ResponseEntity<ApiError> res = rest.exchange(
                "/api/v1/me/password", HttpMethod.POST,
                jsonBearer(token, new ChangePasswordRequest("Admin@12345", "short")), ApiError.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // --- Change 1: auth rate limiting -------------------------------------

    @Test
    void authEndpointReturns429AfterThreshold() {
        // Default capacity is 10 / 60s per IP per endpoint group. The TestRestTemplate
        // calls share one client IP, so the 11th call to /auth/refresh is rejected.
        // /auth/refresh is used (not phone/verify) so this test owns its own group
        // and isn't perturbed by service-level phone verifications elsewhere.
        HttpStatus last = null;
        boolean saw429 = false;
        for (int i = 0; i < 11; i++) {
            ResponseEntity<String> res = rest.exchange(
                    "/api/v1/auth/refresh", HttpMethod.POST,
                    json(new RefreshRequest("definitely-not-a-real-token")), String.class);
            last = HttpStatus.valueOf(res.getStatusCode().value());
            if (last == HttpStatus.TOO_MANY_REQUESTS) {
                saw429 = true;
                assertThat(res.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNotBlank();
                break;
            }
            // Before the limit, an invalid refresh token is a 401 (InvalidCredentials).
            assertThat(last).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
        assertThat(saw429).as("expected a 429 within the first 11 requests").isTrue();
    }

    @Test
    void logoutIsExemptFromRateLimit() {
        // Logout is exempt: 20 calls all succeed (idempotent 204), never 429.
        for (int i = 0; i < 20; i++) {
            ResponseEntity<Void> res = rest.exchange(
                    "/api/v1/auth/logout", HttpMethod.POST,
                    json(new LogoutRequest("never-issued")), Void.class);
            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }
    }

    // --- helpers ----------------------------------------------------------

    private static <T> HttpEntity<T> jsonBearer(String token, T body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }

    private static <T> HttpEntity<T> json(T body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
