package ru.nstu.system.event.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import ru.nstu.system.contracts.messaging.EventPublisher;
import ru.nstu.system.contracts.outbox.JdbcOutboxPublisher;
import ru.nstu.system.contracts.outbox.NstuOutboxProperties;
import ru.nstu.system.contracts.outbox.OutboxWriter;

/**
 * Transactional-outbox wiring for {@code event-service} (design.md D13).
 *
 * <p>The shared {@code ru.nstu.system.contracts} package is part of the component
 * scan, so {@code NstuRabbitConfig} (exchange, DLQ, converter, publisher,
 * {@code @EnableScheduling}) and {@code NstuOutboxProperties} are already
 * registered. This configuration only adds the two collaborators that the library
 * leaves to each service: the {@link OutboxWriter} and the
 * {@link JdbcOutboxPublisher}, both bound to the {@code event} schema via
 * {@code nstu.outbox.schema}.</p>
 */
@Configuration
public class EventMessagingConfig {

    @Bean
    public OutboxWriter outboxWriter(JdbcTemplate jdbcTemplate, NstuOutboxProperties outboxProperties) {
        return new OutboxWriter(jdbcTemplate, outboxProperties);
    }

    @Bean
    public JdbcOutboxPublisher jdbcOutboxPublisher(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            EventPublisher eventPublisher,
            NstuOutboxProperties outboxProperties) {
        return new JdbcOutboxPublisher(jdbcTemplate, transactionManager, eventPublisher, outboxProperties);
    }
}
