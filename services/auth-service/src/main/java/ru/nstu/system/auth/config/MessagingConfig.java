package ru.nstu.system.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import ru.nstu.system.contracts.messaging.EventPublisher;
import ru.nstu.system.contracts.messaging.NstuRabbitConfig;
import ru.nstu.system.contracts.outbox.JdbcOutboxPublisher;
import ru.nstu.system.contracts.outbox.NstuOutboxProperties;
import ru.nstu.system.contracts.outbox.OutboxWriter;

/**
 * Transactional-outbox and RabbitMQ wiring for {@code auth-service}
 * (design.md D2, D13).
 *
 * <p>{@code auth-service} scans only {@code ru.nstu.system.auth} and
 * {@code ru.nstu.system.security}, so the shared contracts package is not picked
 * up automatically. {@link NstuRabbitConfig} is therefore imported explicitly and
 * {@link NstuOutboxProperties} is registered via
 * {@link EnableConfigurationProperties}; the outbox writer and publisher are
 * declared as beans here, bound to the {@code auth} schema.</p>
 *
 * <p>{@link EventPublisher} comes from {@link NstuRabbitConfig} (a
 * {@code RabbitEventPublisher} over the auto-configured {@code RabbitTemplate}),
 * and {@link NstuRabbitConfig}'s {@code @EnableScheduling} drives the publisher
 * poll.</p>
 */
@Configuration
@Import(NstuRabbitConfig.class)
@EnableConfigurationProperties(NstuOutboxProperties.class)
public class MessagingConfig {

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
