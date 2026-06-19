package com.townbasket;

import static org.assertj.core.api.Assertions.assertThat;

import com.townbasket.identity.AuthResponse;
import com.townbasket.identity.AuthService;
import com.townbasket.identity.PhoneVerifyRequest;
import com.townbasket.identity.StaffLoginRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * End-to-end security test over real HTTP (RANDOM_PORT + TestRestTemplate). Boots
 * against a real Postgres (same singleton container pattern as
 * {@code AbstractIntegrationTest}; replicated here because that base class boots
 * a MOCK servlet environment whereas this test needs a live port).
 *
 * <p>Verifies the §1 matrix server-side: an admin route is 401 without a token,
 * 403 with a CUSTOMER token and 200 with an ADMIN token; a public catalog route
 * is 200 without any token.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityIntegrationTest {

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
    @LocalServerPort
    int port;

    @Test
    void publicCatalogEndpointIsReachableWithoutToken() {
        ResponseEntity<String> res = rest.getForEntity("/api/v1/categories", String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void adminOrdersRequiresStaffOrAdmin() {
        // No token -> 401.
        ResponseEntity<String> anon = rest.getForEntity("/api/v1/admin/orders", String.class);
        assertThat(anon.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // CUSTOMER token -> 403.
        AuthResponse customer = authService.phoneVerify(new PhoneVerifyRequest("dev:9888800000"));
        ResponseEntity<String> forbidden = rest.exchange(
                "/api/v1/admin/orders", HttpMethod.GET, bearer(customer.accessToken()), String.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // ADMIN token -> 200.
        AuthResponse admin = authService.staffLogin(new StaffLoginRequest("admin@townbasket.local", "Admin@12345"));
        ResponseEntity<String> ok = rest.exchange(
                "/api/v1/admin/orders", HttpMethod.GET, bearer(admin.accessToken()), String.class);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void meRequiresAuthentication() {
        ResponseEntity<String> anon = rest.getForEntity("/api/v1/me", String.class);
        assertThat(anon.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        AuthResponse customer = authService.phoneVerify(new PhoneVerifyRequest("dev:9888800001"));
        ResponseEntity<String> ok = rest.exchange(
                "/api/v1/me", HttpMethod.GET, bearer(customer.accessToken()), String.class);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void placingAnOrderRequiresAuthentication() {
        // Use a RestTemplate with output streaming DISABLED: the default JDK
        // HttpURLConnection throws "cannot retry ... in streaming mode" on a 401
        // response to a streamed POST. With streaming off the 401 surfaces normally
        // (as HttpClientErrorException, since a plain RestTemplate throws on 4xx).
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setOutputStreaming(false);
        RestTemplate http = new RestTemplate(factory);
        String url = "http://localhost:" + port + "/api/v1/orders";

        // No token -> 401 (no guest checkout).
        assertThat(postStatus(http, url, null)).isEqualTo(HttpStatus.UNAUTHORIZED);

        // With a CUSTOMER token the request clears the auth gate (then a 4xx body
        // validation error, but NOT 401).
        AuthResponse customer = authService.phoneVerify(new PhoneVerifyRequest("dev:9888800002"));
        assertThat(postStatus(http, url, customer.accessToken()))
                .isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /** POST an empty JSON body and return the HTTP status (4xx, which RestTemplate throws, included). */
    private static HttpStatus postStatus(RestTemplate http, String url, String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (bearerToken != null) {
            headers.setBearerAuth(bearerToken);
        }
        try {
            return HttpStatus.valueOf(
                    http.postForEntity(url, new HttpEntity<>("{}", headers), String.class)
                            .getStatusCode().value());
        } catch (HttpClientErrorException e) {
            return HttpStatus.valueOf(e.getStatusCode().value());
        }
    }

    private static HttpEntity<Void> bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }
}
