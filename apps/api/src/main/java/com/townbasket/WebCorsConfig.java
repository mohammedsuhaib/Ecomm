package com.townbasket;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS for the customer PWA and admin dashboard. The storefront makes some
 * calls from the browser (e.g. the serviceability gate, which needs the
 * browser Geolocation API), so the API must allow those cross-origin requests.
 *
 * <p>Allowed origins are configurable via {@code app.cors.allowed-origins}
 * (comma-separated); the default covers local dev (storefront :3000, admin
 * :3001). Production sets the real domains via the environment.
 */
@Configuration
class WebCorsConfig implements WebMvcConfigurer {

    private final List<String> allowedOrigins;

    WebCorsConfig(
            @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:3001}")
            List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
