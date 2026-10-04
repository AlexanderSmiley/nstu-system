package ru.nstu.system.student;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Containers shared by the {@code student-service} integration tests, started once
 * per test JVM.
 *
 * <p>A single PostgreSQL 16 instance and a single RabbitMQ broker keep the Spring
 * test context cacheable (dynamic properties stay identical across test classes)
 * and avoid paying the container startup cost per class. The containers are never
 * stopped explicitly: the JVM exit reaps them.</p>
 */
final class StudentTestContainers {

    // currentSchema mirrors docker-compose: the schema does not exist yet, so it
    // must be created by Flyway (spring.flyway.create-schemas=true).
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withUrlParam("currentSchema", "student");

    static final RabbitMQContainer RABBIT =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:3-management-alpine"));

    static {
        POSTGRES.start();
        RABBIT.start();
    }

    private StudentTestContainers() {
    }
}
