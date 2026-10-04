package ru.nstu.system.contracts.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import ru.nstu.system.contracts.events.AccountCreatedPayload;
import ru.nstu.system.contracts.events.DomainEvent;
import ru.nstu.system.contracts.events.EventTypes;
import ru.nstu.system.contracts.idempotency.IdempotencyGuard;
import ru.nstu.system.contracts.idempotency.IdempotentHandler;
import ru.nstu.system.contracts.idempotency.InMemoryIdempotencyGuard;

/**
 * End-to-end RabbitMQ test (design.md D13): publishing a {@link DomainEvent} is
 * received by a listener; a re-delivered event is handled only once; a poisoned
 * message is dead-lettered and does not block the next message.
 */
@Testcontainers
@SpringBootTest(classes = RabbitMessagingTest.TestApplication.class)
class RabbitMessagingTest {

    // Literal because the value is used in the @RabbitListener annotation,
    // which requires a compile-time constant.
    static final String TEST_QUEUE = "nstu.events.test";

    @Container
    static final RabbitMQContainer RABBIT =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:3-management-alpine"));

    @DynamicPropertySource
    static void rabbitProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
        registry.add("spring.rabbitmq.publisher-confirm-type", () -> "correlated");
        registry.add("spring.rabbitmq.publisher-returns", () -> true);
    }

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private RabbitTestListener listener;

    @Autowired
    private IdempotencyGuard idempotencyGuard;

    @BeforeEach
    void resetState() {
        listener.reset();
        if (idempotencyGuard instanceof InMemoryIdempotencyGuard inMemory) {
            inMemory.clear();
        }
        amqpAdmin.purgeQueue(TEST_QUEUE);
        amqpAdmin.purgeQueue(RabbitNames.DLQ);
    }

    private static DomainEvent<AccountCreatedPayload> accountEvent(UUID eventId, String type) {
        return new DomainEvent<>(
                eventId,
                type,
                EventTypes.CURRENT_VERSION,
                Instant.now(),
                new AccountCreatedPayload(UUID.randomUUID(), "student", "STUDENT", "Иван"));
    }

    @Test
    void publishesEventAndListenerReceivesIt() {
        DomainEvent<AccountCreatedPayload> event = accountEvent(UUID.randomUUID(), EventTypes.ACCOUNT_CREATED);

        UUID returnedId = eventPublisher.publish(event);

        assertThat(returnedId).isEqualTo(event.eventId());
        await().atMost(Duration.ofSeconds(20))
                .until(() -> listener.processed(event.eventId()));
        assertThat(listener.receivedCount()).isEqualTo(1);
    }

    @Test
    void redeliveredEventIsHandledOnlyOnce() {
        DomainEvent<AccountCreatedPayload> event = accountEvent(UUID.randomUUID(), EventTypes.ACCOUNT_CREATED);
        DomainEvent<AccountCreatedPayload> sentinel = accountEvent(UUID.randomUUID(), EventTypes.ACCOUNT_CREATED);

        eventPublisher.publish(event);
        eventPublisher.publish(event);
        eventPublisher.publish(sentinel);

        // The sentinel is processed after the two duplicates (single consumer),
        // so observing it guarantees the duplicates were handled.
        await().atMost(Duration.ofSeconds(20))
                .until(() -> listener.processed(sentinel.eventId()));
        assertThat(listener.invocationCount(event.eventId())).isEqualTo(1);
    }

    @Test
    void poisonMessageIsDeadLetteredAndDoesNotBlockFollowingEvents() {
        DomainEvent<AccountCreatedPayload> poison =
                accountEvent(UUID.randomUUID(), RabbitTestListener.POISON_EVENT_TYPE);
        DomainEvent<AccountCreatedPayload> good =
                accountEvent(UUID.randomUUID(), EventTypes.ACCOUNT_CREATED);

        eventPublisher.publish(poison);
        eventPublisher.publish(good);

        await().atMost(Duration.ofSeconds(20))
                .until(() -> listener.processed(good.eventId()));

        Message deadLettered = rabbitTemplate.receive(RabbitNames.DLQ, 15_000);
        assertThat(deadLettered).isNotNull();
        assertThat(new String(deadLettered.getBody(), StandardCharsets.UTF_8))
                .contains(poison.eventId().toString());
    }

    @SpringBootApplication
    @Import(NstuRabbitConfig.class)
    static class TestApplication {

        @Bean
        Queue testQueue() {
            return QueueBuilder.durable(TEST_QUEUE)
                    .deadLetterExchange(RabbitNames.DLX)
                    .build();
        }

        @Bean
        Binding testBinding(
                @Qualifier("testQueue") Queue testQueue,
                @Qualifier("nstuEventsExchange") TopicExchange exchange) {
            return BindingBuilder.bind(testQueue)
                    .to(exchange)
                    .with(RabbitNames.MATCH_ALL_ROUTING_KEY);
        }

        @Bean
        IdempotencyGuard idempotencyGuard() {
            return new InMemoryIdempotencyGuard();
        }

        @Bean
        IdempotentHandler idempotentHandler(IdempotencyGuard idempotencyGuard) {
            return new IdempotentHandler(idempotencyGuard);
        }
    }
}
