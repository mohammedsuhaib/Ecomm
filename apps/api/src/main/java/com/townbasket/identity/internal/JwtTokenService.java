package com.townbasket.identity.internal;

import com.townbasket.identity.AuthenticatedUser;
import com.townbasket.identity.Role;
import com.townbasket.identity.TokenService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies our own HS256 access tokens (JJWT) and mints opaque
 * rotating refresh tokens. Only a SHA-256 hash of a refresh token is ever
 * persisted (see {@link #hashRefreshToken(String)}); the raw value is handed to
 * the client once at issuance and never logged.
 *
 * <p>Implements the published {@link TokenService} (validation, used by the
 * root security filter); the issuance helpers are package-internal and used by
 * {@code AuthServiceImpl}.
 */
@Service
class JwtTokenService implements TokenService {

    private static final String TYPE_CLAIM = "typ";
    private static final String ACCESS_TYPE = "access";
    private static final String ROLE_CLAIM = "role";
    private static final int REFRESH_TOKEN_BYTES = 32; // 256 bits of entropy.

    private final SecretKey key;
    private final Duration accessTtl;
    private final Duration refreshTtl;
    private final SecureRandom random = new SecureRandom();

    JwtTokenService(JwtProperties props) {
        String secret = props.getSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "townbasket.security.jwt.secret (JWT_SECRET) must be set and >= 32 bytes for HS256");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtl = props.getAccessTtl();
        this.refreshTtl = props.getRefreshTtl();
    }

    @Override
    public Optional<AuthenticatedUser> authenticate(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(accessToken)
                    .getPayload();
            if (!ACCESS_TYPE.equals(claims.get(TYPE_CLAIM, String.class))) {
                return Optional.empty();
            }
            Long userId = Long.valueOf(claims.getSubject());
            Role role = Role.valueOf(claims.get(ROLE_CLAIM, String.class));
            return Optional.of(new AuthenticatedUser(userId, role));
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            // Malformed / expired / bad signature / unknown role -> unauthenticated.
            return Optional.empty();
        }
    }

    /** Mint a fresh HS256 access token for the user. */
    String issueAccessToken(Long userId, Role role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(ROLE_CLAIM, role.name())
                .claim(TYPE_CLAIM, ACCESS_TYPE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .signWith(key)
                .compact();
    }

    /** A new opaque refresh token (base64url of 32 random bytes). */
    String newRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 hash (hex) of a raw refresh token — the only form persisted. */
    String hashRefreshToken(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    Instant refreshExpiry() {
        return Instant.now().plus(refreshTtl);
    }
}
