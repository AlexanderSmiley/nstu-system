package ru.nstu.system.notification;

import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * RabbitMQ container shared by the {@code notification-service} integration tests,
 * started once per test JVM.
 *
 * <p>No database container is needed: the service is a stateless stub (design.md
 * D22) and deduplicates in memory. The container is never stopped explicitly: the
 * JVM exit reaps it.</p>
 */
final class NotificationTestContainers {

    static final RabbitMQContainer RABBIT =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:3-management-alpine"));

    static {
        RABBIT.start();
    }

    private NotificationTestContainers() {
    }
}
