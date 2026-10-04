package ru.nstu.system.contracts.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.nstu.system.contracts.events.AccountCreatedPayload;
import ru.nstu.system.contracts.events.DomainEvent;
import ru.nstu.system.contracts.events.EventTypes;
import ru.nstu.system.contracts.idempotency.JdbcIdempotencyGuard;
import ru.nstu.system.contracts.messaging.EventPublisher;

/**
 * PostgreSQL-backed test of the outbox writer/publisher and the durable
 * idempotency guard (design.md D13): jsonb payloads, {@code FOR UPDATE SKIP
 * LOCKED} claiming, retry bookkeeping and deduplication.
 */
@Testcontainers
class JdbcOutboxSupportTest {

    private static final String SCHEMA = "app";

    private static final String OUTBOX_DDL = """
            CREATE TABLE IF NOT EXISTS app.outbox (
                event_id     uuid PRIMARY KEY,
                event_type   text NOT NULL,
                payload      jsonb NOT NULL,
                created_at   timestamptz NOT NULL DEFAULT now(),
                published_at timestamptz,
                attempts     int NOT NULL DEFAULT 0)
            """;

    private static final String PROCESSED_EVENT_DDL = """
            CREATE TABLE IF NOT EXISTS app.processed_event (
                event_id     uuid PRIMARY KEY,
                processed_at timestamptz NOT NULL DEFAULT now())
            """;

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private JdbcTemplate jdbcTemplate;

    private DataSourceTransactionManager transactionManager;

    private NstuOutboxProperties outboxProperties;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        jdbcTemplate = new JdbcTemplate(dataSource);
        transactionManager = new DataSourceTransactionManager(dataSource);

        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS " + SCHEMA);
        jdbcTemplate.execute(OUTBOX_DDL);
        jdbcTemplate.execute(PROCESSED_EVENT_DDL);
        jdbcTemplate.execute("TRUNCATE TABLE app.outbox");
        jdbcTemplate.execute("TRUNCATE TABLE app.processed_event");

        outboxProperties = new NstuOutboxProperties();
        outboxProperties.setSchema(SCHEMA);
        outboxProperties.setBatchSize(10);
    }

    private static DomainEvent<AccountCreatedPayload> event() {
        return new DomainEvent<>(
                UUID.randomUUID(),
                EventTypes.ACCOUNT_CREATED,
                EventTypes.CURRENT_VERSION,
                Instant.parse("2026-09-27T10:15:30Z"),
                new AccountCreatedPayload(UUID.randomUUID(), "student", "STUDENT", "Иван"));
    }

    @Test
    void writerStoresEnvelopeAndPublisherMarksItPublished() {
        CapturingPublisher capturing = new CapturingPublisher();
        OutboxWriter writer = new OutboxWriter(jdbcTemplate, outboxProperties);
        JdbcOutboxPublisher publisher =
                new JdbcOutboxPublisher(jdbcTemplate, transactionManager, capturing, outboxProperties);
        DomainEvent<AccountCreatedPayload> event = event();

        writer.write(event);
        publisher.publishPending();

        assertThat(capturing.events).hasSize(1);
        DomainEvent<?> published = capturing.events.get(0);
        assertThat(published.eventId()).isEqualTo(event.eventId());
        assertThat(published.eventType()).isEqualTo(EventTypes.ACCOUNT_CREATED);
        assertThat(published.version()).isEqualTo(EventTypes.CURRENT_VERSION);
        assertThat(published.occurredAt()).isEqualTo(event.occurredAt());
        assertThat(published.payload()).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) published.payload()).get("username")).isEqualTo("student");

        assertThat(publishedFlag(event.eventId())).isTrue();
        assertThat(attempts(event.eventId())).isZero();
    }

    @Test
    void failingPublisherIncrementsAttemptsAndKeepsRowUnpublished() {
        EventPublisher failing = domainEvent -> {
            throw new IllegalStateException("broker unavailable");
        };
        OutboxWriter writer = new OutboxWriter(jdbcTemplate, outboxProperties);
        JdbcOutboxPublisher publisher =
                new JdbcOutboxPublisher(jdbcTemplate, transactionManager, failing, outboxProperties);
        DomainEvent<AccountCreatedPayload> event = event();

        writer.write(event);
        publisher.publishPending();

        assertThat(publishedFlag(event.eventId())).isFalse();
        assertThat(attempts(event.eventId())).isEqualTo(1);
    }

    @Test
    void jdbcIdempotencyGuardDeduplicatesByEventId() {
        JdbcIdempotencyGuard guard = new JdbcIdempotencyGuard(jdbcTemplate, SCHEMA);
        UUID eventId = UUID.randomUUID();

        assertThat(guard.alreadyProcessed(eventId)).isFalse();
        guard.markProcessed(eventId);
        assertThat(guard.alreadyProcessed(eventId)).isTrue();
        guard.markProcessed(eventId);
        guard.markProcessed(eventId);

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM app.processed_event", Integer.class);
        assertThat(rows).isEqualTo(1);
    }

    private boolean publishedFlag(UUID eventId) {
        Boolean published = jdbcTemplate.queryForObject(
                "SELECT published_at IS NOT NULL FROM app.outbox WHERE event_id = ?",
                Boolean.class, eventId);
        return Boolean.TRUE.equals(published);
    }

    private int attempts(UUID eventId) {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT attempts FROM app.outbox WHERE event_id = ?", Integer.class, eventId);
        return value == null ? -1 : value;
    }

    private static final class CapturingPublisher implements EventPublisher {

        private final List<DomainEvent<?>> events = new ArrayList<>();

        @Override
        public UUID publish(DomainEvent<?> event) {
            events.add(event);
            return event.eventId();
        }
    }
}
