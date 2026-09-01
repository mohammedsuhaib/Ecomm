package com.townbasket.identity.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.townbasket.identity.InvalidCredentialsException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.LocatorAdapter;
import io.jsonwebtoken.ProtectedHeader;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * Production phone-token verifier: validates a Firebase ID token as a
 * Google-signed RS256 JWT. Active ONLY when
 * {@code townbasket.identity.firebase.project-id} holds a NON-BLANK value
 * (see {@link PhoneVerifierMode}).
 *
 * <p>Verification per Firebase's spec: fetch Google's public x509 signing certs,
 * select by {@code kid}, verify the RS256 signature and the standard claims —
 * {@code iss = https://securetoken.google.com/<projectId>},
 * {@code aud = <projectId>}, and {@code exp} (handled by JJWT). The {@code sub}
 * is the stable Firebase uid; {@code phone_number} is the E.164 phone.
 *
 * <p>Certs are refetched lazily when an unknown {@code kid} appears (Google
 * rotates them). No third-party Firebase SDK is used (deliberately).
 */
@Component
@Conditional(FirebaseConfiguredCondition.class)
class FirebasePhoneTokenVerifier implements PhoneTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(FirebasePhoneTokenVerifier.class);

    private static final String CERT_URL =
            "https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com";

    private final String projectId;
    private final String expectedIssuer;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CertificateFactory certFactory;

    private volatile Map<String, Key> signingKeys = Map.of();

    FirebasePhoneTokenVerifier(
            @Value("${townbasket.identity.firebase.project-id}") String projectId) {
        this.projectId = projectId;
        // Which verifier is live, and the projectId a token must match, is the
        // first thing you need when a login 401s — and it is not visible from
        // the response, which stays deliberately vague.
        log.info("Phone-OTP: REAL Firebase verifier active for projectId '{}' "
                + "(dev:<phone> tokens are rejected)", projectId);
        this.expectedIssuer = "https://securetoken.google.com/" + projectId;
        try {
            this.certFactory = CertificateFactory.getInstance("X.509");
        } catch (Exception e) {
            throw new IllegalStateException("X.509 certificate factory unavailable", e);
        }
    }

    @Override
    public VerifiedPhone verify(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            log.warn("Phone token rejected: empty token");
            throw new InvalidCredentialsException("Invalid phone token");
        }
        if (idToken.startsWith("dev:")) {
            // The commonest half-configured deployment: backend switched to real
            // OTP, storefront still built without NEXT_PUBLIC_FIREBASE_* so it
            // sends the offline token. Say so instead of "invalid token".
            log.warn("Phone token rejected: received a dev: token while the REAL verifier is "
                    + "active. The frontend was built WITHOUT the Firebase web config — rebuild "
                    + "it with the NEXT_PUBLIC_FIREBASE_* build args, or unset "
                    + "townbasket.identity.firebase.project-id to go back to the offline verifier.");
            throw new InvalidCredentialsException("Invalid phone token");
        }
        try {
            Jws<Claims> jws = Jwts.parser()
                    .keyLocator(new CertKeyLocator())
                    .requireIssuer(expectedIssuer)
                    .requireAudience(projectId)
                    .build()
                    .parseSignedClaims(idToken);
            Claims claims = jws.getPayload();
            String uid = claims.getSubject();
            String phone = claims.get("phone_number", String.class);
            if (uid == null || uid.isBlank() || phone == null || phone.isBlank()) {
                log.warn("Phone token rejected: signature and issuer are valid but sub/phone_number "
                        + "is missing — the token is not from a phone sign-in");
                throw new InvalidCredentialsException("Phone token missing sub/phone_number");
            }
            return new VerifiedPhone(uid, phone);
        } catch (JwtException | IllegalArgumentException e) {
            // Overwhelmingly a projectId mismatch: the verifier requires
            // iss=https://securetoken.google.com/<projectId> and aud=<projectId>
            // exactly, and Firebase project ids often carry a generated suffix.
            log.warn("Phone token rejected while verifying against projectId '{}' "
                    + "(expected issuer {}): {}. If the token came from a different Firebase "
                    + "project, townbasket.identity.firebase.project-id does not match the one "
                    + "the frontend was built with.", projectId, expectedIssuer, e.getMessage());
            throw new InvalidCredentialsException("Invalid phone token");
        }
    }

    /** Resolves the RS256 verification key for a token's {@code kid}, refetching certs on a miss. */
    private final class CertKeyLocator extends LocatorAdapter<Key> {
        @Override
        protected Key locate(ProtectedHeader header) {
            String kid = header.getKeyId();
            if (kid == null) {
                log.warn("Phone token rejected: no kid header, so it is not a Google-signed token");
                throw new InvalidCredentialsException("Phone token has no key id");
            }
            Key key = signingKeys.get(kid);
            if (key == null) {
                refreshKeys();
                key = signingKeys.get(kid);
            }
            if (key == null) {
                log.warn("Phone token rejected: signing key '{}' is unknown even after refetching "
                        + "Google's certs", kid);
                throw new InvalidCredentialsException("Unknown phone token signing key");
            }
            return key;
        }
    }

    private void refreshKeys() {
        try {
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(URI.create(CERT_URL))
                            .timeout(Duration.ofSeconds(5)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            JsonNode certs = objectMapper.readTree(response.body());
            Map<String, Key> keys = new HashMap<>();
            certs.fields().forEachRemaining(entry -> {
                try {
                    X509Certificate cert = (X509Certificate) certFactory.generateCertificate(
                            new ByteArrayInputStream(entry.getValue().asText().getBytes(StandardCharsets.UTF_8)));
                    keys.put(entry.getKey(), cert.getPublicKey());
                } catch (Exception ignored) {
                    // Skip a malformed cert entry; others still load.
                }
            });
            this.signingKeys = Map.copyOf(keys);
        } catch (Exception e) {
            log.warn("Could not fetch Google's token-signing certs from {} — every phone login "
                    + "will fail until this succeeds (check egress from the API container): {}",
                    CERT_URL, e.getMessage());
            throw new InvalidCredentialsException("Could not fetch Firebase signing keys");
        }
    }
}
