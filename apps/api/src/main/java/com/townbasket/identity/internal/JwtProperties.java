package com.townbasket.identity.internal;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT signing config under {@code townbasket.security.jwt}. The HS256 signing
 * {@code secret} comes from the environment ({@code JWT_SECRET}); a clearly
 * marked dev default lives in {@code application.yml}. It MUST be at least 32
 * bytes (256 bits) for HS256.
 */
@ConfigurationProperties(prefix = "townbasket.security.jwt")
class JwtProperties {

    private String secret;
    private Duration accessTtl = Duration.ofMinutes(15);
    private Duration refreshTtl = Duration.ofDays(30);

    String getSecret() {
        return secret;
    }

    void setSecret(String secret) {
        this.secret = secret;
    }

    Duration getAccessTtl() {
        return accessTtl;
    }

    void setAccessTtl(Duration accessTtl) {
        this.accessTtl = accessTtl;
    }

    Duration getRefreshTtl() {
        return refreshTtl;
    }

    void setRefreshTtl(Duration refreshTtl) {
        this.refreshTtl = refreshTtl;
    }
}
