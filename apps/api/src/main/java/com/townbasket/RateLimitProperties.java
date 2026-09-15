package com.townbasket;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Per-IP auth rate-limit settings under {@code townbasket.security.ratelimit}.
 * A fixed window of {@link #window} allows up to {@link #capacity} requests per
 * client IP per limited endpoint group. Sane defaults (60 requests / 60s) are
 * baked in so the limiter works without any configuration.
 *
 * <p><strong>Why the default is 60 and not a handful.</strong> An IP is not a
 * person here: a shop's WiFi, a shared office connection or a mobile carrier's
 * CGNAT pool puts a whole group of customers behind ONE public address, so a
 * tight per-IP budget locks out a crowd signing in together (which is a normal
 * Saturday, not an attack). The credential-guessing job this number used to be
 * doing is now done per account, where sharing an IP is irrelevant, by the
 * identity module's staff-login failure throttle
 * ({@code townbasket.security.login-throttle}) — so this budget can be sized for
 * "a busy minute of real people" while password guessing stays bounded.
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
            capacity = 60;
        }
        if (window == null) {
            window = Duration.ofSeconds(60);
        }
    }
}
