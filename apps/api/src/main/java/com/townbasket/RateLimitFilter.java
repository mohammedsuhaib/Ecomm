package com.townbasket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.townbasket.shared.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * In-memory, per-client-IP rate limiter for the auth endpoints (ARCHITECTURE §7)
 * to blunt brute-force / OTP abuse on a single droplet — no Redis, no extra deps.
 *
 * <p>Limited (one bucket per IP, per endpoint group):
 * {@code POST /api/v1/auth/phone/verify}, {@code POST /api/v1/auth/staff/login},
 * {@code POST /api/v1/auth/refresh}. {@code POST /api/v1/auth/logout} is exempt.
 * Any other path that reaches this filter passes straight through (it is
 * registered only for {@code /api/v1/auth/*}).
 *
 * <p>Algorithm: a hand-rolled fixed window. Each bucket holds a window-start
 * timestamp and a counter; the (capacity+1)-th request inside the window is
 * rejected with {@code 429} + a {@code Retry-After} header and the shared
 * {@link ApiError} JSON body (written here because the filter runs outside the
 * {@code @RestControllerAdvice}). Stale buckets (window fully elapsed) are evicted
 * periodically (at most once per window, never on the per-request hot path) so the
 * map cannot grow unbounded.
 *
 * <p>Client IP: the first hop of {@code X-Forwarded-For} when present (we sit
 * behind Caddy in prod), else {@code request.getRemoteAddr()}.
 *
 * <p>Root-package infrastructure (like {@link JwtAuthenticationFilter}); keeps the
 * identity module free of any dependency on it.
 */
class RateLimitFilter extends OncePerRequestFilter {

    private static final String PHONE_VERIFY = "/api/v1/auth/phone/verify";
    private static final String STAFF_LOGIN = "/api/v1/auth/staff/login";
    private static final String REFRESH = "/api/v1/auth/refresh";

    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Window> buckets = new ConcurrentHashMap<>();
    // Guards eviction so it runs at most once per window (single thread via CAS),
    // not an O(n) full-map scan on every request — avoids a CPU amplifier under
    // high key cardinality (e.g. many distinct client IPs).
    private final AtomicLong lastEvictionMillis = new AtomicLong();

    RateLimitFilter(RateLimitProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String group = limitedGroup(request);
        if (group == null) {
            chain.doFilter(request, response); // not a limited endpoint (e.g. logout) -> pass through
            return;
        }

        long now = System.currentTimeMillis();
        long windowMillis = properties.getWindow().toMillis();
        evictStale(now, windowMillis);

        String key = clientIp(request) + '|' + group;
        Window window = buckets.compute(key, (k, existing) -> {
            if (existing == null || now - existing.startMillis >= windowMillis) {
                return new Window(now);
            }
            return existing;
        });

        int used = window.count.incrementAndGet();
        if (used > properties.getCapacity()) {
            long retryAfterSeconds = Math.max(1, (windowMillis - (now - window.startMillis) + 999) / 1000);
            reject(request, response, retryAfterSeconds);
            return;
        }
        chain.doFilter(request, response);
    }

    /** The limited endpoint group for this request, or {@code null} if not limited. */
    private static String limitedGroup(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return null;
        }
        String path = request.getRequestURI();
        if (PHONE_VERIFY.equals(path)) {
            return PHONE_VERIFY;
        }
        if (STAFF_LOGIN.equals(path)) {
            return STAFF_LOGIN;
        }
        if (REFRESH.equals(path)) {
            return REFRESH;
        }
        return null;
    }

    /**
     * First hop of {@code X-Forwarded-For} (behind Caddy in prod), else the socket address.
     *
     * <p>OPS NOTE (security): this trusts the inbound {@code X-Forwarded-For}. A directly
     * reachable client could forge/rotate it to get a fresh bucket per request and bypass
     * the per-IP limit, so the limiter is only effective if the fronting proxy OVERWRITES
     * it with the real peer. In production the API MUST sit behind Caddy with
     * {@code trusted_proxies} configured so any client-supplied {@code X-Forwarded-For} is
     * stripped/replaced (Caddy's {@code reverse_proxy} sets it to the immediate client).
     * If the API is ever exposed directly, switch this to {@code getRemoteAddr()} only.
     * (Tracked for the M6 prod deploy / Caddy config.)
     */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            String first = (comma >= 0 ? forwarded.substring(0, comma) : forwarded).trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        return request.getRemoteAddr();
    }

    private void evictStale(long now, long windowMillis) {
        // Throttle: only scan when at least a window has passed since the last eviction,
        // and let a single thread win the scan (CAS). Keeps the map bounded to keys
        // active within ~2 windows without an O(n) sweep on every request.
        long last = lastEvictionMillis.get();
        if (now - last >= windowMillis && lastEvictionMillis.compareAndSet(last, now)) {
            buckets.values().removeIf(w -> now - w.startMillis >= windowMillis);
        }
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, long retryAfterSeconds)
            throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json");
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        ApiError body = ApiError.of(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                "Too many requests; please retry later",
                request.getRequestURI());
        objectMapper.writeValue(response.getWriter(), body);
    }

    /** A fixed window: its start time and a request counter. */
    private static final class Window {
        private final long startMillis;
        private final AtomicInteger count = new AtomicInteger();

        private Window(long startMillis) {
            this.startMillis = startMillis;
        }
    }
}
