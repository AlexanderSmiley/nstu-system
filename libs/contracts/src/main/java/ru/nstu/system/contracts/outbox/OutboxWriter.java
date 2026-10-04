package ru.nstu.system.contracts.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.nstu.system.contracts.events.DomainEvent;
import ru.nstu.system.contracts.events.EventJson;
import ru.nstu.system.contracts.support.SqlIdentifiers;

/**
 * Writes a domain event into the service-owned {@code outbox} table
 * (design.md D13).
 *
 * <p>The insert must run inside the same transaction as the domain change, so
 * the event is either committed together with the change or not at all. The
 * full {@link DomainEvent} envelope is stored in the {@code payload} column
 * (jsonb), which preserves {@code version} and {@code occurredAt} for the
 * publisher.</p>
 */
public class OutboxWriter {

    public static final String TABLE = "outbox";

    private final JdbcTemplate jdbcTemplate;

    private final String qualifiedTable;

    private final ObjectMapper objectMapper;

    public OutboxWriter(JdbcTemplate jdbcTemplate, NstuOutboxProperties properties) {
        this(jdbcTemplate, properties.getSchema(), EventJson.objectMapper());
    }

    public OutboxWriter(JdbcTemplate jdbcTemplate, String schema) {
        this(jdbcTemplate, schema, EventJson.objectMapper());
    }

    public OutboxWriter(JdbcTemplate jdbcTemplate, String schema, ObjectMapper objectMapper) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.qualifiedTable = SqlIdentifiers.qualify(schema, TABLE);
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    /**
     * Appends the event to the outbox.
     *
     * @return the {@code eventId} written
     */
    public UUID write(DomainEvent<?> event) {
        Objects.requireNonNull(event, "event");
        String payload = toJson(event);
        jdbcTemplate.update(
                "INSERT INTO " + qualifiedTable
                        + " (event_id, event_type, payload, created_at) VALUES (?, ?, ?::jsonb, ?)",
                event.eventId(),
                event.eventType(),
                payload,
                Timestamp.from(event.occurredAt()));
        return event.eventId();
    }

    private String toJson(DomainEvent<?> event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "cannot serialise domain event " + event.eventId(), e);
        }
    }
}
