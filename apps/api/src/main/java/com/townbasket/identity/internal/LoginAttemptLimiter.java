package com.townbasket.identity.internal;

import com.townbasket.shared.TooManyRequestsException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Bounds password guessing PER STAFF ACCOUNT by counting only FAILED
 * {@code /auth/staff/login} attempts; a successful login clears the account's
 * counter.
 *
 * <p><strong>Why this exists alongside the per-IP limiter.</strong> The edge
 * limiter ({@code RateLimitFilter}) buckets by client IP, and a shop's WiFi or a
 * mobile carrier's CGNAT puts many legitimate people on ONE public IP — so its
 * budget has to be big enough for a crowd signing in at once, which is far more
 * than a password attack needs. Keying on the account instead removes the
 * trade-off: a crowd behind one IP is unaffected (each person has their own
 * key, and success clears it), while guessing one account's password is capped
 * regardless of how many IPs the attacker rotates through.
 *
 * <p><strong>Only failures count</strong>, which is what makes the two
 * properties above possible: normal sign-ins never consume budget, so staff who
 * log in repeatedly (or a test suite that does) can never lock themselves out.
 *
 * <p>Unknown emails are counted too — that is what bounds account enumeration —
 * and the 429 is byte-identical for a known and an unknown address, so it leaks
 * nothing the 401 didn't already.
 *
 * <p>In-memory and single-instance, like every other counter here (ARCHITECTURE
 * §7): one JVM, no Redis. Deliberately NOT sharing code with
 * {@code RateLimitFilter}'s window — that one counts every request to shed load
 * at the edge and writes its own HTTP response, this one counts failures,
 * resets on success and throws; fusing them would mean a utility with two modes
 * and no clearer behaviour.
 */
@Component
class LoginAttemptLimiter {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptLimiter.class);

    private final LoginThrottleProperties properties;

    /** account key (normalised email) -> failures in the current window. */
    private final Map<String, Failures> failures = new ConcurrentHashMap<>();

    /** Guards the stale-key sweep so it runs at most once per window (CAS). */
    private final AtomicLong lastEvictionMillis = new AtomicLong();

    LoginAttemptLimiter(LoginThrottleProperties properties) {
        this.properties = properties;
    }

    /**
     * Refuse the attempt outright when this account is over its failure budget.
     * Called BEFORE any password work, so a locked-out account costs no BCrypt.
     *
     * @throws TooManyRequestsException mapped to 429 + {@code Retry-After}
     */
    void check(String accountKey) {
        long now = System.currentTimeMillis();
        long windowMillis = properties.window().toMillis();
        evictStale(now, windowMillis);

        Failures current = failures.get(accountKey);
        if (current == null) {
            return;
        }
        long elapsed = now - current.startMillis;
        if (elapsed >= windowMillis) {
            // Window has run out; drop it (guarded so a concurrent fresh window
            // registered by recordFailure is not discarded).
            failures.remove(accountKey, current);
            return;
        }
        if (current.count.get() >= properties.maxFailures()) {
            long retryAfterSeconds = (windowMillis - elapsed + 999) / 1000;
            // No account key in the message on purpose: the key is a login
            // email, and the "Staff login failed for user {}" lines that had to
            // come first already name the account (or report that there isn't
            // one, which is the enumeration signal). Sequence over PII.
            log.warn("Staff login throttled: an account is over its budget of {} failures "
                            + "per {}; refusing for another {}s",
                    properties.maxFailures(), properties.window(), retryAfterSeconds);
            throw new TooManyRequestsException(
                    "Too many failed sign-in attempts for this account; please try again later",
                    retryAfterSeconds);
        }
    }

    /** Count one failed attempt. The window starts at the first failure in it. */
    void recordFailure(String accountKey) {
        long now = System.currentTimeMillis();
        long windowMillis = properties.window().toMillis();
        failures.compute(accountKey, (key, existing) -> {
            if (existing == null || now - existing.startMillis >= windowMillis) {
                return new Failures(now);
            }
            return existing;
        }).count.incrementAndGet();
    }

    /** A correct password proves the caller is not guessing: forget the failures. */
    void clear(String accountKey) {
        failures.remove(accountKey);
    }

    private void evictStale(long now, long windowMillis) {
        // Keys are attacker-supplied (any email reaches this map), so the sweep is
        // what keeps it bounded — but off the hot path: one window between scans,
        // one thread per scan.
        long last = lastEvictionMillis.get();
        if (now - last >= windowMillis && lastEvictionMillis.compareAndSet(last, now)) {
            failures.values().removeIf(f -> now - f.startMillis >= windowMillis);
        }
    }

    /** Failures counted since {@link #startMillis}. */
    private static final class Failures {
        private final long startMillis;
        private final AtomicInteger count = new AtomicInteger();

        private Failures(long startMillis) {
            this.startMillis = startMillis;
        }
    }
}
