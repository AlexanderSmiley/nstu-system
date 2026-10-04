package ru.nstu.system.auth;

import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Single PostgreSQL 16 container shared by every authentication integration test
 * (started once per test JVM). Sharing keeps the Spring test context cacheable and
 * avoids starting a database per test class.
 */
final class PostgresTestContainer {

    // currentSchema mirrors docker-compose: the schema does not exist yet, so it
    // must be created by Flyway (spring.flyway.create-schemas=true).
    static final PostgreSQLContainer<?> INSTANCE = new PostgreSQLContainer<>("postgres:16-alpine")
            .withUrlParam("currentSchema", "auth");

    static {
        INSTANCE.start();
    }

    private PostgresTestContainer() {
    }
}
