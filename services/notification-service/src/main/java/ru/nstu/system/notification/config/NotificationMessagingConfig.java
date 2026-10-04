package ru.nstu.system.notification.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.nstu.system.contracts.idempotency.IdempotencyGuard;
import ru.nstu.system.contracts.idempotency.IdempotentHandler;
import ru.nstu.system.contracts.idempotency.InMemoryIdempotencyGuard;
import ru.nstu.system.contracts.messaging.NstuQueueNames;
import ru.nstu.system.contracts.messaging.RabbitNames;

/**
 * RabbitMQ wiring owned by {@code notification-service} (design.md D13, D22).
 *
 * <p>The shared {@code NstuRabbitConfig} declares the {@code nstu.events}
 * exchange and the DLX/DLQ pair. This service declares its own durable queue
 * bound with {@link RabbitNames#MATCH_ALL_ROUTING_KEY}, so it receives every
 * domain event type. Rejected messages are dead-lettered
 * ({@code x-dead-letter-exchange = }{@link RabbitNames#DLX}) and later re-driven
 * by {@link ru.nstu.system.notification.messaging.DlqReDriveScheduler}.</p>
 *
 * <p>Unlike the domain services, {@code notification-service} is a stateless
 * stub: it owns no schema and stores nothing. Deduplication therefore uses the
 * process-local {@link InMemoryIdempotencyGuard} ({@code notification-service}
 * has no JDBC datasource); see the service README/design note for the
 * trade-off.</p>
 */
@Configuration
public class NotificationMessagingConfig {

    /** Short service name; the queue name must match {@link NstuQueueNames}. */
    public static final String SERVICE_NAME = "notification";

    /**
     * Durable queue of this service. Declared as a literal because the value is
     * used in the {@code @RabbitListener} annotation, which needs a compile-time
     * constant; {@code queueNameMatchesSharedConvention} in the tests keeps it in
     * sync with {@link NstuQueueNames#forService(String)}.
     */
    public static final String NOTIFICATION_EVENTS_QUEUE = "nstu.events.notification";

    /** Queue receiving every event; dead-letters to {@link RabbitNames#DLX}. */
    @Bean
    public Queue notificationEventsQueue() {
        return QueueBuilder.durable(NOTIFICATION_EVENTS_QUEUE)
                .deadLetterExchange(RabbitNames.DLX)
                .build();
    }

    /** Subscribe to all domain events via the {@code #} pattern. */
    @Bean
    public Binding notificationEventsBinding(
            @Qualifier("notificationEventsQueue") Queue notificationEventsQueue,
            @Qualifier("nstuEventsExchange") TopicExchange exchange) {
        return BindingBuilder.bind(notificationEventsQueue)
                .to(exchange)
                .with(RabbitNames.MATCH_ALL_ROUTING_KEY);
    }

    /**
     * Listener factory with the retry policy from {@code
     * spring.rabbitmq.listener.simple.retry.*}. {@code defaultRequeueRejected=false}
     * matches the shared factory: an unhandled failure is dead-lettered instead of
     * being requeued forever.
     */
    @Bean
    public SimpleRabbitListenerContainerFactory notificationRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter,
            RabbitProperties rabbitProperties) {

        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setDefaultRequeueRejected(false);

        RabbitProperties.SimpleContainer simple = rabbitProperties.getListener().getSimple();
        factory.setAutoStartup(simple.isAutoStartup());

        RabbitProperties.Retry retry = simple.getRetry();
        if (retry.isEnabled()) {
            factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                    .maxAttempts(retry.getMaxAttempts())
                    .backOffOptions(
                            retry.getInitialInterval().toMillis(),
                            retry.getMultiplier(),
                            retry.getMaxInterval().toMillis())
                    .recoverer(new RejectAndDontRequeueRecoverer())
                    .build());
        }
        return factory;
    }

    /** Process-local deduplication; a stateless stub does not need a schema. */
    @Bean
    public InMemoryIdempotencyGuard idempotencyGuard() {
        return new InMemoryIdempotencyGuard();
    }

    @Bean
    public IdempotentHandler idempotentHandler(IdempotencyGuard idempotencyGuard) {
        return new IdempotentHandler(idempotencyGuard);
    }
}
