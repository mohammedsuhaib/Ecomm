package com.townbasket.identity.internal;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Staff-login failure throttle under {@code townbasket.security.login-throttle}:
 * at most {@link #maxFailures} FAILED password attempts per account within
 * {@link #window}. A successful login clears the account's counter.
 *
 * <p>This is the per-credential companion to the per-IP edge limiter
 * ({@code townbasket.security.ratelimit}). The edge limiter has to be generous
 * because many legitimate users share one public IP behind NAT/CGNAT, which
 * leaves password guessing to be bounded here, per account, where sharing an IP
 * is irrelevant.
 *
 * <p>Defaults (10 failures / 5 minutes) bound an attacker to ~120 guesses per
 * hour against a given account while staying clear of a human mistyping their
 * password a few times.
 *
 * @param maxFailures failed attempts allowed per account within one window
 * @param window      the fixed window, measured from the first failure in it
 */
@ConfigurationProperties(prefix = "townbasket.security.login-throttle")
record LoginThrottleProperties(int maxFailures, Duration window) {

    LoginThrottleProperties {
        if (maxFailures <= 0) {
            maxFailures = 10;
        }
        if (window == null) {
            window = Duration.ofMinutes(5);
        }
    }
}
