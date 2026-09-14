package com.townbasket;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.header.HeaderWriter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Decides the {@code Cache-Control} of every response: a short public window for
 * the handful of anonymous read endpoints whose body is the same for everyone,
 * and {@code no-store} for all the rest.
 *
 * <p><strong>The bug this fixes.</strong> Spring Security installs
 * {@code CacheControlHeadersWriter} by default, so <em>every</em> response left
 * this API with {@code Cache-Control: no-cache, no-store, max-age=0,
 * must-revalidate}, {@code Pragma: no-cache} and {@code Expires: 0} — the
 * product catalogue included, though it is byte-identical for every visitor.
 * Browsers, the storefront's Next.js fetch cache and any reverse proxy were all
 * forbidden from reusing a response. ARCHITECTURE §4 claimed the opposite.
 *
 * <p><strong>Why a {@code HeaderWriter} and not an interceptor.</strong> The
 * obvious implementation — a handler interceptor that sets the header in
 * {@code postHandle}, relying on Security's writer skipping a response that
 * already has one — works for most endpoints and silently fails for the rest.
 * Security writes its headers when the response is <em>committed</em>, and a
 * small response body written straight to Tomcat commits during
 * {@code handleReturnValue}, i.e. before {@code postHandle} runs; the interceptor
 * then finds {@code no-store} already in place and backs off. It was measured
 * happening: {@code /serviceability/check} (a 90-byte body, no response
 * buffering in front of it) kept {@code no-store} while the four larger
 * catalogue endpoints took the new header. A {@code HeaderWriter} replaces
 * Security's own, so it runs at exactly the moment Security's did — there is no
 * race left to lose.
 *
 * <p>The default branch reproduces Security's three headers verbatim rather than
 * leaving them off: {@code Pragma} and {@code Expires} are what stop an HTTP/1.0
 * cache storing a cart or an order, and disabling the built-in writer removes
 * those too.
 */
class PublicReadCacheHeaderWriter implements HeaderWriter {

    /** Exactly what Spring Security's {@code CacheControlHeadersWriter} emits. */
    private static final String NO_STORE = "no-cache, no-store, max-age=0, must-revalidate";

    /** A public-read endpoint and the {@code Cache-Control} it should carry. */
    private record Rule(RequestMatcher matcher, String cacheControl) {}

    private final List<Rule> rules = List.of(
            // Categories: the storefront nav. Edited a few times a year.
            publicFor(Duration.ofMinutes(5), Duration.ofHours(1), "/api/v1/categories"),

            // Products (listing, search, detail). A short window because these
            // carry live stock counts — though a stale count is harmless, since
            // the cart and checkout both re-check stock against the database.
            // One path segment, not "/**": the listing, /products/search and
            // /products/{idOrSlug} are all that exist, and a wildcard over the
            // whole subtree would silently mark a future nested endpoint
            // (/products/{id}/something-personal) publicly cacheable.
            publicFor(Duration.ofSeconds(60), Duration.ofMinutes(5),
                    "/api/v1/products", "/api/v1/products/*"),

            // Store details: the payload's `open` flag flips at the opening and
            // closing times, and staff can close the shop at any moment, so keep
            // this well inside the storefront's own 5-minute status poll.
            publicFor(Duration.ofSeconds(30), Duration.ofMinutes(2), "/api/v1/store"),

            // Serviceability of one lat/lng: a pure function of the store's
            // location and delivery radius, neither of which moves.
            publicFor(Duration.ofMinutes(5), Duration.ofMinutes(30),
                    "/api/v1/serviceability/check"));

    @Override
    public void writeHeaders(HttpServletRequest request, HttpServletResponse response) {
        // A handler that set the header itself (a signed download URL, say) keeps it.
        if (response.containsHeader(HttpHeaders.CACHE_CONTROL)) {
            return;
        }
        String cacheable = publicCacheControlFor(request, response);
        if (cacheable != null) {
            response.setHeader(HttpHeaders.CACHE_CONTROL, cacheable);
            return;
        }
        response.setHeader(HttpHeaders.CACHE_CONTROL, NO_STORE);
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        response.setHeader(HttpHeaders.EXPIRES, "0");
    }

    /**
     * The public {@code Cache-Control} for this exchange, or null when the
     * response must not be stored.
     *
     * <p>The status check matters as much as the path: an interceptor or writer
     * sees the failures too, and a cacheable 404 or 500 is worse than an uncached
     * success — a transient error would be served back for the whole window. 304
     * is allowed alongside 200 because the conditional-GET filter answers a
     * revalidation with one, and a 304 that said {@code no-store} would tell the
     * client to drop the very entry it just revalidated.
     */
    private String publicCacheControlFor(HttpServletRequest request, HttpServletResponse response) {
        int status = response.getStatus();
        if (status != HttpStatus.OK.value() && status != HttpStatus.NOT_MODIFIED.value()) {
            return null;
        }
        for (Rule rule : rules) {
            if (rule.matcher().matches(request)) {
                return rule.cacheControl();
            }
        }
        return null;
    }

    /**
     * {@code public} is accurate for these paths: they need no credentials and
     * their bodies do not vary by caller, so a shared cache may serve one copy to
     * everyone. {@code stale-while-revalidate} lets a cache keep serving the old
     * copy while it fetches a new one, turning an expiry into a background
     * refresh instead of a wait.
     */
    private static Rule publicFor(Duration maxAge, Duration staleWhileRevalidate, String... getPaths) {
        String value = CacheControl.maxAge(maxAge)
                .cachePublic()
                .staleWhileRevalidate(staleWhileRevalidate)
                .getHeaderValue();
        List<RequestMatcher> matchers = Arrays.stream(getPaths)
                .map(path -> (RequestMatcher) new AntPathRequestMatcher(path, "GET"))
                .toList();
        RequestMatcher any = request -> matchers.stream().anyMatch(m -> m.matches(request));
        return new Rule(any, value);
    }
}
