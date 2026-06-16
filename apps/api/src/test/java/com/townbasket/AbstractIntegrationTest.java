package com.townbasket;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
}
