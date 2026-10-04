package ru.nstu.system.notification;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import ru.nstu.system.contracts.idempotency.InMemoryIdempotencyGuard;
import ru.nstu.system.contracts.messaging.EventPublisher;
import ru.nstu.system.contracts.messaging.RabbitNames;
import ru.nstu.system.notification.config.NotificationMessagingConfig;
import ru.nstu.system.notification.messaging.DlqReDriveScheduler;

/**
 * Shared setup for the {@code notification-service} integration tests (tasks
 * 10.1, 10.2).
 *
 * <p>One Spring context and one RabbitMQ broker are reused. No database is
 * involved. Listener retry is limited to a single attempt so a failure lands in
 * the DLQ immediately; the re-drive schedule is pushed far away and tests call
 * {@link DlqReDriveScheduler#reDrive()} explicitly.</p>
 */
@SpringBootTest(properties = {
        "nstu.dlq.redrive-interval=PT24H",
        "spring.rabbitmq.listener.simple.retry.enabled=true",
        "spring.rabbitmq.listener.simple.retry.max-attempts=1",
        "spring.rabbitmq.listener.simple.retry.initial-interval=10ms"
})
@Import(NotificationTestSupport.class)
abstract class AbstractNotificationIntegrationTest {

    @Autowired
    protected EventPublisher eventPublisher;

    @Autowired
    protected RabbitTemplate rabbitTemplate;

    @Autowired
    protected AmqpAdmin amqpAdmin;

    @Autowired
    protected InMemoryIdempotencyGuard idempotencyGuard;

    @Autowired
    protected TestNotificationEventHandler testEventHandler;

    @Autowired
    protected DlqReDriveScheduler dlqReDriveScheduler;

    @DynamicPropertySource
    static void rabbitProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", NotificationTestContainers.RABBIT::getHost);
        registry.add("spring.rabbitmq.port", NotificationTestContainers.RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", NotificationTestContainers.RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", NotificationTestContainers.RABBIT::getAdminPassword);
    }

    @BeforeEach
    void resetState() {
        idempotencyGuard.clear();
        testEventHandler.reset();
        purgeQueue(NotificationMessagingConfig.NOTIFICATION_EVENTS_QUEUE);
        purgeQueue(RabbitNames.DLQ);
    }

    protected int queueMessageCount(String queue) {
        var info = amqpAdmin.getQueueInfo(queue);
        return info == null ? 0 : info.getMessageCount();
    }

    protected static Duration awaitTimeout() {
        return Duration.ofSeconds(30);
    }

    private void purgeQueue(String queue) {
        try {
            amqpAdmin.purgeQueue(queue);
        } catch (RuntimeException ex) {
            // The queue may legitimately not be declared yet on the very first
            // run; tests publish afterwards, so this is safe to ignore.
        }
    }
}
