package com.townbasket;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * The caching policy, pinned. Every assertion here stands for a way the previous
 * behaviour was wrong or a way a future edit could make it wrong again: the
 * catalogue used to be uncacheable, and the danger in fixing that is marking
 * something cacheable that must not be — a cart, an error, a write.
 *
 * <p>No Spring context: the writer is a plain object over the servlet API, so
 * these run in milliseconds and in CI without Docker.
 */
class PublicReadCacheHeaderWriterTest {

    private final PublicReadCacheHeaderWriter writer = new PublicReadCacheHeaderWriter();

    private MockHttpServletResponse write(String method, String uri, int status) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        // Boot maps the DispatcherServlet at "/", so in a running app the servlet
        // path IS the whole path — and that is what Spring Security's request
        // matchers read (the same ones SecurityConfig's authorization rules use).
        // MockHttpServletRequest leaves it blank, which would make every matcher
        // miss and turn the negative assertions below into vacuous passes.
        request.setServletPath(uri);
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(status);
        writer.writeHeaders(request, response);
        return response;
    }

    private String cacheControl(String method, String uri, int status) {
        return write(method, uri, status).getHeader("Cache-Control");
    }

    @Test
    void publicCatalogueReadsAreCacheableWithTheirOwnWindows() {
        assertThat(cacheControl("GET", "/api/v1/categories", 200))
                .isEqualTo("max-age=300, public, stale-while-revalidate=3600");
        assertThat(cacheControl("GET", "/api/v1/products", 200))
                .isEqualTo("max-age=15, public, stale-while-revalidate=60");
        assertThat(cacheControl("GET", "/api/v1/products/amul-butter", 200))
                .isEqualTo("max-age=15, public, stale-while-revalidate=60");
        assertThat(cacheControl("GET", "/api/v1/store", 200))
                .isEqualTo("max-age=30, public, stale-while-revalidate=120");
        // private, not public: the URL carries the customer's coordinates, so a
        // shared cache must not key an entry on where someone lives.
        assertThat(cacheControl("GET", "/api/v1/serviceability/check", 200))
                .isEqualTo("max-age=300, private, stale-while-revalidate=1800");
    }

    @Test
    void searchIsNeverPubliclyCacheableDespiteMatchingTheProductsWindow() {
        // A search is someone asking whether something is in stock RIGHT NOW,
        // and the storefront sends it no-store for exactly that reason. The
        // server used to contradict its own client here — /api/v1/products/*
        // matched, so the browser cache was told it could hold the answer for
        // 15s and serve it stale for 60 more, which is how a just-restocked
        // product still came back unavailable.
        MockHttpServletResponse response = write("GET", "/api/v1/products/search", 200);
        assertThat(response.getHeader("Cache-Control"))
                .isEqualTo("no-cache, no-store, max-age=0, must-revalidate");
        assertThat(response.getHeader("Pragma")).isEqualTo("no-cache");
        assertThat(response.getHeader("Expires")).isEqualTo("0");

        // The query string must not let it back into the window either. Built
        // by hand because the helper above puts the whole string in the servlet
        // path, and a real request carries the query separately — matchers
        // never see it.
        MockHttpServletRequest withQuery =
                new MockHttpServletRequest("GET", "/api/v1/products/search");
        withQuery.setServletPath("/api/v1/products/search");
        withQuery.setQueryString("q=atta");
        MockHttpServletResponse queried = new MockHttpServletResponse();
        queried.setStatus(200);
        writer.writeHeaders(withQuery, queried);
        assertThat(queried.getHeader("Cache-Control"))
                .isEqualTo("no-cache, no-store, max-age=0, must-revalidate");

        // The browse listing keeps its window — it is browsed, not asked.
        assertThat(cacheControl("GET", "/api/v1/products", 200))
                .isEqualTo("max-age=15, public, stale-while-revalidate=60");
    }

    @Test
    void everythingElseKeepsTheFullNoStoreTriple() {
        // Carts, orders, the customer profile and the whole admin surface are
        // per-user or privileged: a shared cache must never hold one.
        for (String uri : new String[] {
                "/api/v1/carts/mine",
                "/api/v1/orders/mine",
                "/api/v1/orders/track/8d1f1f6c-0000-0000-0000-000000000000",
                "/api/v1/me",
                "/api/v1/admin/orders",
                "/api/v1/admin/store"}) {
            MockHttpServletResponse response = write("GET", uri, 200);
            assertThat(response.getHeader("Cache-Control"))
                    .as(uri)
                    .isEqualTo("no-cache, no-store, max-age=0, must-revalidate");
            // Pragma and Expires are what stop an HTTP/1.0 cache; disabling
            // Security's own writer removed them, so this writer must send them.
            assertThat(response.getHeader("Pragma")).as(uri).isEqualTo("no-cache");
            assertThat(response.getHeader("Expires")).as(uri).isEqualTo("0");
        }
    }

    @Test
    void theProductsMatcherDoesNotCoverNestedPaths() {
        // The matcher is one path segment deep on purpose. If it were
        // /products/**, adding any nested endpoint later — a per-customer
        // review, a saved-for-later flag — would silently publish it to shared
        // caches, with nothing in the diff that added it to mention caching.
        assertThat(cacheControl("GET", "/api/v1/products/42/anything", 200))
                .isEqualTo("no-cache, no-store, max-age=0, must-revalidate");
    }

    @Test
    void adminReadsOfTheSameResourcesAreNeverCacheable() {
        // The admin catalogue lives under /api/v1/admin/catalog and returns
        // cost_price; it must not be caught by the public /products matchers.
        for (String uri : new String[] {
                "/api/v1/admin/catalog/products",
                "/api/v1/admin/catalog/categories",
                "/api/v1/admin/analytics/revenue"}) {
            assertThat(cacheControl("GET", uri, 200))
                    .as(uri)
                    .isEqualTo("no-cache, no-store, max-age=0, must-revalidate");
        }
    }

    @Test
    void onlyReadsAreCacheable() {
        // A POST to /api/v1/products would be an admin write; the path matching
        // must not make its response cacheable.
        assertThat(cacheControl("POST", "/api/v1/products", 200))
                .isEqualTo("no-cache, no-store, max-age=0, must-revalidate");
        assertThat(cacheControl("PUT", "/api/v1/store", 200))
                .isEqualTo("no-cache, no-store, max-age=0, must-revalidate");
    }

    @Test
    void failuresAreNeverCacheable() {
        // An unknown slug 404s and a broken dependency 500s. Caching either would
        // serve the failure back for the whole window, long after it was fixed.
        assertThat(cacheControl("GET", "/api/v1/products/no-such-slug", 404))
                .isEqualTo("no-cache, no-store, max-age=0, must-revalidate");
        assertThat(cacheControl("GET", "/api/v1/products", 500))
                .isEqualTo("no-cache, no-store, max-age=0, must-revalidate");
        assertThat(cacheControl("GET", "/api/v1/store", 503))
                .isEqualTo("no-cache, no-store, max-age=0, must-revalidate");
    }

    @Test
    void notModifiedKeepsThePublicWindow() {
        // The conditional-GET filter answers a revalidation with 304. Sending
        // no-store on it would tell the client to discard the entry it just
        // revalidated, turning every 304 into a guaranteed miss next time.
        assertThat(cacheControl("GET", "/api/v1/products", 304))
                .isEqualTo("max-age=15, public, stale-while-revalidate=60");
    }

    @Test
    void aHandlerThatSetItsOwnHeaderKeepsIt() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/products");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setHeader("Cache-Control", "private, max-age=5");

        writer.writeHeaders(request, response);

        assertThat(response.getHeader("Cache-Control")).isEqualTo("private, max-age=5");
    }
}
