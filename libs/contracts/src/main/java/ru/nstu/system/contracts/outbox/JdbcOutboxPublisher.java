package ru.nstu.system.contracts.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.nstu.system.contracts.events.DomainEvent;
import ru.nstu.system.contracts.events.EventJson;
import ru.nstu.system.contracts.messaging.EventPublisher;
import ru.nstu.system.contracts.support.SqlIdentifiers;

/**
 * Moves committed outbox rows to RabbitMQ (design.md D13).
 *
 * <p>Each poll runs in a single transaction and claims a batch with
 * {@code FOR UPDATE SKIP LOCKED}, so several instances can poll concurrently
 * without stepping on each other. A row is marked {@code published_at = now()}
 * only after the broker accepted the message; failures increment
 * {@code attempts} and leave the row for the next poll. Publication is
 * therefore at-least-once and consumers must be idempotent.</p>
 *
 * <p>The polling interval is read from {@code nstu.outbox.poll-interval}
 * (ISO-8601 duration, default {@code PT5S}).</p>
 *
 * <p>Expected table layout (see {@code db/outbox.sql}):
 * {@code event_id uuid primary key, event_type text, payload jsonb,
 * created_at timestamptz, published_at timestamptz, attempts int}.</p>
 */
public class JdbcOutboxPublisher {

    public static final int DEFAULT_BATCH_SIZE = NstuOutboxProperties.DEFAULT_BATCH_SIZE;

    private static final Logger log = LoggerFactory.getLogger(JdbcOutboxPublisher.class);

    private final JdbcTemplate jdbcTemplate;

    private final EventPublisher eventPublisher;

    private final TransactionTemplate transactionTemplate;

    private final String qualifiedTable;

    private final String selectSql;

    private final String markPublishedSql;

    private final String incrementAttemptsSql;

    private final ObjectMapper objectMapper;

    private final int batchSize;

    public JdbcOutboxPublisher(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            EventPublisher eventPublisher,
            NstuOutboxProperties properties) {
        this(jdbcTemplate, transactionManager, eventPublisher,
                properties.getSchema(), properties.getBatchSize(), EventJson.objectMapper());
    }

    public JdbcOutboxPublisher(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            EventPublisher eventPublisher,
            String schema) {
        this(jdbcTemplate, transactionManager, eventPublisher,
                schema, DEFAULT_BATCH_SIZE, EventJson.objectMapper());
    }

    public JdbcOutboxPublisher(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            EventPublisher eventPublisher,
            String schema,
            int batchSize,
            ObjectMapper objectMapper) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        Objects.requireNonNull(transactionManager, "transactionManager");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        this.batchSize = batchSize;
        this.qualifiedTable = SqlIdentifiers.qualify(schema, OutboxWriter.TABLE);
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.selectSql = "SELECT event_id, payload::text AS payload FROM " + qualifiedTable
                + " WHERE published_at IS NULL ORDER BY created_at LIMIT ?"
                + " FOR UPDATE SKIP LOCKED";
        this.markPublishedSql = "UPDATE " + qualifiedTable
                + " SET published_at = now() WHERE event_id = ?";
        this.incrementAttemptsSql = "UPDATE " + qualifiedTable
                + " SET attempts = attempts + 1 WHERE event_id = ?";
    }

    /** Claims and publishes one batch of unpublished events. */
    @Scheduled(fixedDelayString = "${nstu.outbox.poll-interval:PT5S}")
    public void publishPending() {
        int published = transactionTemplate.execute(status -> publishBatch());
        if (published > 0) {
            log.debug("Outbox: published {} event(s)", published);
        }
    }

    private int publishBatch() {
        List<OutboxRow> rows = jdbcTemplate.query(
                selectSql,
                (rs, rowNum) -> new OutboxRow(
                        rs.getObject("event_id", UUID.class),
                        rs.getString("payload")),
                batchSize);

        int published = 0;
        for (OutboxRow row : rows) {
            if (publishSingle(row)) {
                published++;
            }
        }
        return published;
    }

    private boolean publishSingle(OutboxRow row) {
        try {
            DomainEvent<?> event = objectMapper.readValue(row.payload(), DomainEvent.class);
            eventPublisher.publish(event);
            jdbcTemplate.update(markPublishedSql, row.eventId());
            return true;
        } catch (JsonProcessingException e) {
            log.error("Outbox: cannot deserialise event {}; moving past it", row.eventId(), e);
            incrementAttempts(row.eventId());
            return false;
        } catch (RuntimeException e) {
            log.error("Outbox: failed to publish event {}; will retry", row.eventId(), e);
            incrementAttempts(row.eventId());
            return false;
        }
    }

    private void incrementAttempts(UUID eventId) {
        jdbcTemplate.update(incrementAttemptsSql, eventId);
    }

    private record OutboxRow(UUID eventId, String payload) {
    }
}
