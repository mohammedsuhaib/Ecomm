package com.townbasket;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Per-IP auth rate-limit settings under {@code townbasket.security.ratelimit}.
 * A fixed window of {@link #window} allows up to {@link #capacity} requests per
 * client IP per limited endpoint group. Sane defaults (10 requests / 60s) are
 * baked in so the limiter works without any configuration.
 *
 * <p>Root-package infrastructure (not an identity-module concern): the limiter
 * is a servlet filter that runs ahead of the controllers.
 *
 * @param capacity          max requests allowed per client IP per endpoint group
 *                          within one window
 * @param window            the fixed window length
 * @param trustForwardedFor whether to derive the client IP from the
 *                          {@code X-Forwarded-For} header. Default {@code false}
 *                          (secure): use the socket peer address
 *                          ({@code getRemoteAddr()}), which a client cannot forge.
 *                          A directly-reachable client could otherwise
 *                          spoof/rotate {@code X-Forwarded-For} to get a fresh
 *                          bucket per request and bypass the per-IP limit
 *                          entirely. Enable this ONLY when the API sits behind a
 *                          trusted reverse proxy (Caddy/Nginx) that OVERWRITES
 *                          the header with the real peer.
 */
@ConfigurationProperties(prefix = "townbasket.security.ratelimit")
record RateLimitProperties(int capacity, Duration window, boolean trustForwardedFor) {

    RateLimitProperties {
        if (capacity <= 0) {
            capacity = 10;
        }
        if (window == null) {
            window = Duration.ofSeconds(60);
        }
    }
}
