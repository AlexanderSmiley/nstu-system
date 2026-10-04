package ru.nstu.system.student.config;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import ru.nstu.system.contracts.events.EventTypes;
import ru.nstu.system.contracts.idempotency.IdempotencyGuard;
import ru.nstu.system.contracts.idempotency.IdempotentHandler;
import ru.nstu.system.contracts.idempotency.JdbcIdempotencyGuard;
import ru.nstu.system.contracts.idempotency.NstuIdempotencyProperties;
import ru.nstu.system.contracts.messaging.EventPublisher;
import ru.nstu.system.contracts.messaging.NstuQueueNames;
import ru.nstu.system.contracts.messaging.RabbitNames;
import ru.nstu.system.contracts.outbox.JdbcOutboxPublisher;
import ru.nstu.system.contracts.outbox.NstuOutboxProperties;
import ru.nstu.system.contracts.outbox.OutboxWriter;

/**
 * RabbitMQ wiring owned by {@code student-service} (design.md D13).
 *
 * <p>The shared {@code NstuRabbitConfig} declares the exchange and the DLX/DLQ
 * pair; this service declares its own durable queue bound to
 * {@link EventTypes#ACCOUNT_CREATED} only, so a foreign event can never be handed
 * to the account-created listener. Rejected messages are dead-lettered
 * ({@code x-dead-letter-exchange = }{@link RabbitNames#DLX}) and later re-driven
 * by {@link ru.nstu.system.student.messaging.DlqReDriveScheduler}.</p>
 *
 * <p>A dedicated listener container factory is used instead of the shared one so
 * that the retry policy can be taken from {@code
 * spring.rabbitmq.listener.simple.retry.*}: the retry advice retries with
 * exponential backoff, then {@link RejectAndDontRequeueRecoverer} rejects the
 * message into the DLQ.</p>
 */
@Configuration
public class StudentMessagingConfig {

    /** Short service name; the queue name must match {@link NstuQueueNames}. */
    public static final String SERVICE_NAME = "student";

    /**
     * Durable queue of this service. Declared as a literal because the value is
     * used in the {@code @RabbitListener} annotation, which needs a compile-time
     * constant; {@code queueNameMatchesSharedConvention} in the tests keeps it in
     * sync with {@link NstuQueueNames#forService(String)}.
     */
    public static final String STUDENT_EVENTS_QUEUE = "nstu.events.student";

    /** Queue receiving {@code account.created}; dead-letters to {@link RabbitNames#DLX}. */
    @Bean
    public Queue studentEventsQueue() {
        return QueueBuilder.durable(STUDENT_EVENTS_QUEUE)
                .deadLetterExchange(RabbitNames.DLX)
                .build();
    }

    /** Only {@code account.created} is routed to this service's queue. */
    @Bean
    public Binding studentEventsBinding(
            @Qualifier("studentEventsQueue") Queue studentEventsQueue,
            @Qualifier("nstuEventsExchange") TopicExchange exchange) {
        return BindingBuilder.bind(studentEventsQueue)
                .to(exchange)
                .with(EventTypes.ACCOUNT_CREATED);
    }

    /**
     * Listener factory with the retry policy from {@code
     * spring.rabbitmq.listener.simple.retry.*}. {@code defaultRequeueRejected=false}
     * matches the shared factory: an unhandled failure is dead-lettered instead of
     * being requeued forever.
     */
    @Bean
    public SimpleRabbitListenerContainerFactory studentRabbitListenerContainerFactory(
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

    /** Transactional outbox writer for {@code profile.updated} (design.md D13). */
    @Bean
    public OutboxWriter outboxWriter(JdbcTemplate jdbcTemplate, NstuOutboxProperties properties) {
        return new OutboxWriter(jdbcTemplate, properties);
    }

    /** Polls {@code student.outbox} and publishes committed events (design.md D13). */
    @Bean
    public JdbcOutboxPublisher jdbcOutboxPublisher(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            EventPublisher eventPublisher,
            NstuOutboxProperties properties) {
        return new JdbcOutboxPublisher(jdbcTemplate, transactionManager, eventPublisher, properties);
    }

    /** Durable, cross-restart deduplication of consumed events (design.md D13). */
    @Bean
    public IdempotencyGuard idempotencyGuard(
            JdbcTemplate jdbcTemplate,
            NstuIdempotencyProperties properties) {
        return new JdbcIdempotencyGuard(jdbcTemplate, properties);
    }

    @Bean
    public IdempotentHandler idempotentHandler(IdempotencyGuard idempotencyGuard) {
        return new IdempotentHandler(idempotencyGuard);
    }
}
