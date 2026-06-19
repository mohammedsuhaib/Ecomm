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
 */
@ConfigurationProperties(prefix = "townbasket.security.ratelimit")
class RateLimitProperties {

    /** Max requests allowed per client IP per endpoint group within one window. */
    private int capacity = 10;

    /** The fixed window length. */
    private Duration window = Duration.ofSeconds(60);

    int getCapacity() {
        return capacity;
    }

    void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    Duration getWindow() {
        return window;
    }

    void setWindow(Duration window) {
        this.window = window;
    }
}
