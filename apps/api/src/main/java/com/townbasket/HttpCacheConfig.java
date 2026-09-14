package com.townbasket;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

/**
 * Conditional-GET support for the public read endpoints. The {@code Cache-Control}
 * policy that goes with it lives in {@link PublicReadCacheHeaderWriter}, which
 * has to be a Spring Security header writer to win the race described there.
 */
@Configuration
class HttpCacheConfig {

    /**
     * {@code ETag} + {@code If-None-Match} handling, so a client revalidating
     * after {@code max-age} gets a 304 with no body instead of the payload
     * again — the difference between a 40 KB catalogue page and 200 bytes of
     * headers on every storefront navigation past the cache window.
     *
     * <p>Scoped by URL rather than registered globally because the filter buffers
     * the entire response in memory to hash it. That is fine for a page of
     * products; it must not happen to the per-order SSE tracking stream, whose
     * response never ends, nor to the admin CSV exports.
     *
     * <p>Ordered after Spring Security's chain (which sits at -100) so only
     * responses that already passed authorization are buffered and hashed.
     */
    @Bean
    FilterRegistrationBean<ShallowEtagHeaderFilter> etagFilter() {
        ShallowEtagHeaderFilter filter = new ShallowEtagHeaderFilter();
        // WEAK etags (W/"…"), and this is not cosmetic: Tomcat refuses to gzip a
        // response that carries a STRONG ETag, because compressing it would change
        // the bytes while the tag still claimed byte-for-byte equality. Measured
        // with a strong tag, a 100-product page went out as 31 506 bytes
        // uncompressed with server.compression.enabled=true — the ETag had
        // silently switched compression off for every catalogue response. A weak
        // tag means "semantically equivalent", which is all a conditional GET
        // needs here, and Tomcat compresses it.
        filter.setWriteWeakETag(true);
        FilterRegistrationBean<ShallowEtagHeaderFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns(
                "/api/v1/categories",
                "/api/v1/products",
                "/api/v1/products/*",
                "/api/v1/store",
                "/api/v1/serviceability/check");
        registration.setName("etagFilter");
        registration.setOrder(0);
        return registration;
    }
}
