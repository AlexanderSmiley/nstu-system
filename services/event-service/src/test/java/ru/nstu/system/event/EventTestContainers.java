package ru.nstu.system.event;

import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Single PostgreSQL 16 container shared by the {@code event-service} web
 * integration tests (started once per test JVM).
 *
 * <p>Sharing keeps the Spring test context cacheable (dynamic properties stay
 * identical across subclasses) and avoids paying the container startup cost per
 * class. No RabbitMQ broker is started: the outbox poll interval is pushed far
 * away and the tests assert on committed outbox rows, not on publication.</p>
 */
final class EventTestContainers {

    // currentSchema mirrors docker-compose: the schema does not exist yet, so it
    // must be created by Flyway (spring.flyway.create-schemas=true).
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withUrlParam("currentSchema", "event");

    static {
        POSTGRES.start();
    }

    private EventTestContainers() {
    }
}
