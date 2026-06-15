package com.townbasket;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for integration tests that need a real Postgres (per ARCHITECTURE
 * §8 — "the test DB is real Postgres" via Testcontainers). Boots the full
 * Spring context against a throwaway container so Flyway migrations and the
 * schema-per-module layout are exercised exactly as in production.
 *
 * <p>{@code ModularityTests} deliberately does NOT extend this — boundary
 * verification is a static analysis over the type system and needs no database,
 * so it stays fast and runs without Docker.
 */
@SpringBootTest
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("townbasket")
                    .withUsername("townbasket")
                    .withPassword("townbasket");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        // @ServiceConnection wires the datasource automatically; this hook is
        // kept as the documented extension point for any extra dynamic config.
        registry.add("spring.flyway.enabled", () -> "true");
    }
}
