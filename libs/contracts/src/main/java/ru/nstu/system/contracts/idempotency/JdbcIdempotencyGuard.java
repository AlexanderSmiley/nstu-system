package ru.nstu.system.contracts.idempotency;

import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.nstu.system.contracts.support.SqlIdentifiers;

/**
 * Durable {@link IdempotencyGuard} backed by the {@code processed_event} table.
 *
 * <p>Survives restarts and works across instances because the primary key on
 * {@code event_id} makes {@link #markProcessed(UUID)} a no-op on conflict. The
 * table is created by the consuming service's migration (see
 * {@code db/processed_event.sql}); the schema comes from
 * {@code nstu.idempotency.schema}.</p>
 */
public class JdbcIdempotencyGuard implements IdempotencyGuard {

    public static final String TABLE = "processed_event";

    private final JdbcTemplate jdbcTemplate;

    private final String qualifiedTable;

    private final String existsSql;

    private final String insertSql;

    public JdbcIdempotencyGuard(JdbcTemplate jdbcTemplate, NstuIdempotencyProperties properties) {
        this(jdbcTemplate, properties.getSchema());
    }

    public JdbcIdempotencyGuard(JdbcTemplate jdbcTemplate, String schema) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.qualifiedTable = SqlIdentifiers.qualify(schema, TABLE);
        this.existsSql = "SELECT EXISTS (SELECT 1 FROM " + qualifiedTable + " WHERE event_id = ?)";
        this.insertSql = "INSERT INTO " + qualifiedTable
                + " (event_id) VALUES (?) ON CONFLICT (event_id) DO NOTHING";
    }

    @Override
    public boolean alreadyProcessed(UUID eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Boolean exists = jdbcTemplate.queryForObject(existsSql, Boolean.class, eventId);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public void markProcessed(UUID eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        jdbcTemplate.update(insertSql, eventId);
    }
}
