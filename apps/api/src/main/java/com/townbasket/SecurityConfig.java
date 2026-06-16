package com.townbasket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.townbasket.identity.TokenService;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless JWT security for the API (ARCHITECTURE §7, M4_CONTRACT §1).
 *
 * <p>No HTTP session — every request carries its own access token (validated by
 * {@link JwtAuthenticationFilter}). CSRF is disabled (no cookies/session). CORS
 * is owned here (a single {@link CorsConfigurationSource}) so there is exactly
 * one {@code Access-Control-Allow-Origin} header; the old MVC {@code WebCorsConfig}
 * was removed to avoid duplicate ACAO headers on preflight.
 *
 * <p>Authorization matrix (server-side; the UI is not the gate):
 * <ul>
 *   <li>{@code /api/v1/admin/**} → STORE_STAFF or ADMIN;</li>
 *   <li>{@code /me}, {@code /me/addresses/**}, {@code /orders/mine},
 *       {@code POST /orders/&#42;/reorder}, {@code POST /carts/&#42;/merge} → authenticated;</li>
 *   <li>everything else (catalog, serviceability, public cart/orders, auth,
 *       swagger, actuator health/info) → permitAll.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

    private final List<String> allowedOrigins;

    SecurityConfig(
            @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:3001}")
            List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            TokenService tokenService,
                                            ObjectMapper objectMapper) throws Exception {
        var entryPoint = new ApiErrorAuthEntryPoint(objectMapper);
        var accessDenied = new ApiErrorAccessDeniedHandler(objectMapper);

        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Always allow CORS preflight.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Admin surface — staff/admin only.
                        .requestMatchers("/api/v1/admin/**").hasAnyRole("STORE_STAFF", "ADMIN")

                        // Authenticated (any valid token). These are listed BEFORE the
                        // public catalog/cart/orders rules so the more specific paths win.
                        .requestMatchers("/api/v1/me", "/api/v1/me/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/orders/mine").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/orders/*/reorder").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/carts/*/merge").authenticated()

                        // Everything else is public (catalog, serviceability, the rest of
                        // cart/orders, /auth/**, swagger, actuator health/info, etc.).
                        .anyRequest().permitAll())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDenied))
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .addFilterBefore(new JwtAuthenticationFilter(tokenService),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Single CORS source for {@code /api/**}, owned by Security. Credentials are
     * allowed (the clients send the Bearer token), origins come from
     * {@code app.cors.allowed-origins}, and the standard verbs + all headers are
     * permitted.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
