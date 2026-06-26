package com.townbasket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers the {@link RateLimitFilter} for the auth surface only
 * ({@code /api/v1/auth/*}), so non-auth endpoints are never rate-limited. The
 * filter runs early (highest precedence) — ahead of the Spring Security filter
 * chain — so abusive traffic is shed before any auth/business work.
 *
 * <p>Root-package infrastructure: keeps the identity module free of any
 * dependency on the limiter (and keeps {@code ModularityTests} green).
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
class RateLimitConfiguration {

    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(
            RateLimitProperties properties, ObjectMapper objectMapper) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RateLimitFilter(properties, objectMapper));
        registration.addUrlPatterns("/api/v1/auth/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setName("rateLimitFilter");
        return registration;
    }
}
