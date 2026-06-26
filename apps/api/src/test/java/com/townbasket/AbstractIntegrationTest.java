package com.townbasket;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for integration tests that need a real Postgres (per ARCHITECTURE
 * §8 — "the test DB is real Postgres" via Testcontainers). Boots the full
 * Spring context against a throwaway container so Flyway migrations and the
 * schema-per-module layout are exercised exactly as in production.
 *
 * <p>Uses the <strong>singleton container pattern</strong>: the container is
 * started once in a static initializer and intentionally never stopped per
 * class. Spring caches and shares the application context across all test
 * classes with the same configuration, so a per-class {@code @Container}
 * lifecycle would stop the container after the first class and leave the
 * shared context pointing at a dead datasource (CannotCreateTransaction) for
 * the next class. The singleton stays up for the whole JVM and is reaped by
 * Ryuk at exit. {@code @ServiceConnection} wires Spring Boot's datasource to it.
 *
 * <p>{@code ModularityTests} deliberately does NOT extend this — boundary
 * verification is a static analysis over the type system and needs no database,
 * so it stays fast and runs without Docker.
 */
@SpringBootTest
@Import(AbstractIntegrationTest.FixedClockConfig.class)
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("townbasket")
                    .withUsername("townbasket")
                    .withPassword("townbasket");

    static {
        POSTGRES.start();
    }

    /**
     * Pins the application clock to 12:00 IST so the store open/closed check
     * (seeded hours 08:00–21:00) is deterministic regardless of when CI runs.
     */
    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            // 2024-01-01T06:30:00Z == 12:00 in Asia/Kolkata (UTC+5:30).
            return Clock.fixed(Instant.parse("2024-01-01T06:30:00Z"), ZoneId.of("Asia/Kolkata"));
        }
    }
}
