package com.townbasket.identity.internal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Module-internal wiring for identity: the {@link PasswordEncoder} used for
 * staff/admin password hashing and verification, the JWT configuration
 * properties and the staff-login failure throttle's settings
 * ({@link LoginThrottleProperties}). Kept inside {@code internal} so the
 * encoder and both settings groups are implementation details of the module.
 * Enables scheduling for the refresh-token cleanup job
 * ({@link RefreshTokenCleanup}).
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties({JwtProperties.class, LoginThrottleProperties.class})
class IdentitySupportConfiguration {

    /** BCrypt (cost 10, the seed migration's cost) for staff/admin passwords. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
