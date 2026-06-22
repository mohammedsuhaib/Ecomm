package com.townbasket;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.modulith.Modulith;

/**
 * Town Basket — quick-commerce modular monolith.
 *
 * <p>The application is split into Spring Modulith application modules (one
 * Java package per module under {@code com.townbasket}). Module boundaries are
 * enforced at test time by {@code ModularityTests}; CI fails on violations.
 */
@Modulith(
        systemName = "Town Basket",
        sharedModules = "shared"
)
@SpringBootApplication
public class TownBasketApplication {

    public static void main(String[] args) {
        SpringApplication.run(TownBasketApplication.class, args);
    }

    /**
     * Wall clock used for store open/closed checks, fixed to the store's local
     * time zone (Bangalore/IST) so opening hours are interpreted consistently
     * regardless of the server's zone. Overridable in tests for determinism.
     */
    @Bean
    Clock clock() {
        return Clock.system(ZoneId.of("Asia/Kolkata"));
    }
}
