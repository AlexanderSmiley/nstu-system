package ru.nstu.system.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies the {@code event} Flyway migrations against a throwaway PostgreSQL
 * container: schema creation, the partial unique indexes from design.md D15 and
 * the idempotency of a repeated migration run.
 */
@Testcontainers
@SpringBootTest(properties = {
        // The shared security module fails fast without a JWT secret, and the
        // outbox scheduler must not need a broker during the test (no message is
        // published here anyway).
        "nstu.jwt.secret=nstu-integration-test-secret-0123456789",
        "nstu.outbox.poll-interval=PT24H"
})
@Transactional
class EventSchemaMigrationTest {

    // currentSchema mirrors docker-compose: the schema does not exist yet, so it
    // must be created by Flyway (spring.flyway.create-schemas).
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withUrlParam("currentSchema", "event");

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Flyway flyway;

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
    }

    @Test
    void createsAllEventTablesInDedicatedSchema() {
        List<String> tables = jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'event'",
                String.class);

        assertThat(tables)
                .contains("event", "queue_entry", "outbox", "flyway_schema_history");
    }

    @Test
    void createsPartialUniqueIndexesForActiveEntries() {
        String nameIndex = indexDefinition("queue_entry_active_name_uniq");
        String positionIndex = indexDefinition("queue_entry_active_position_uniq");

        assertThat(nameIndex)
                .contains("UNIQUE")
                .contains("event_id")
                .contains("name_normalized")
                .contains("WHERE")
                .contains("WAITING")
                .contains("PAUSED");
        assertThat(positionIndex)
                .contains("UNIQUE")
                .contains("event_id")
                .contains("position")
                .contains("WHERE")
                .contains("WAITING")
                .contains("PAUSED");
    }

    @Test
    void createsSupportingIndexes() {
        List<String> indexes = jdbc.queryForList(
                "select indexname from pg_indexes where schemaname = 'event'",
                String.class);

        assertThat(indexes).contains(
                "event_status_closed_at_idx",
                "queue_entry_event_status_idx",
                "queue_entry_event_passed_at_idx",
                "outbox_published_at_idx");
    }

    @Test
    void rejectsTwoActiveEntriesWithTheSameNormalizedName() {
        UUID eventId = insertEvent();
        insertEntry(eventId, "Иванов", "иванов", 1, "WAITING");

        assertThatThrownBy(() -> insertEntry(eventId, "ИВАНОВ", "иванов", 2, "PAUSED"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsPassedAndActiveEntriesWithTheSameName() {
        UUID eventId = insertEvent();
        insertEntry(eventId, "Иванов", "иванов", 1, "WAITING");
        insertEntry(eventId, "Иванов", "иванов", 1, "PASSED");

        Integer active = jdbc.queryForObject(
                "select count(*) from event.queue_entry where event_id = ? and status in ('WAITING','PAUSED')",
                Integer.class, eventId);
        Integer passed = jdbc.queryForObject(
                "select count(*) from event.queue_entry where event_id = ? and status = 'PASSED'",
                Integer.class, eventId);

        assertThat(active).isEqualTo(1);
        assertThat(passed).isEqualTo(1);
    }

    @Test
    void rejectsTwoActiveEntriesWithTheSamePosition() {
        UUID eventId = insertEvent();
        insertEntry(eventId, "Иванов", "иванов", 1, "WAITING");

        assertThatThrownBy(() -> insertEntry(eventId, "Петров", "петров", 1, "WAITING"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsPassedEntryToReuseAnActivePosition() {
        UUID eventId = insertEvent();
        insertEntry(eventId, "Иванов", "иванов", 1, "WAITING");
        insertEntry(eventId, "Петров", "петров", 1, "PASSED");

        Integer total = jdbc.queryForObject(
                "select count(*) from event.queue_entry where event_id = ?",
                Integer.class, eventId);

        assertThat(total).isEqualTo(2);
    }

    @Test
    void repeatedMigrationRunIsNoOp() {
        var result = flyway.migrate();

        assertThat(result.migrationsExecuted).isZero();
        assertThat(flyway.info().pending()).isEmpty();
    }

    private String indexDefinition(String indexName) {
        return jdbc.queryForObject(
                "select indexdef from pg_indexes where schemaname = 'event' and indexname = ?",
                String.class, indexName);
    }

    private UUID insertEvent() {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into event.event (id, group_id, title, type, availability, slug, created_by) "
                        + "values (?, ?, ?, ?, ?, ?, ?)",
                id, UUID.randomUUID(), "Лабораторная", "QUEUE", "GUEST+", "slug-" + id, UUID.randomUUID());
        return id;
    }

    private void insertEntry(UUID eventId, String name, String normalized, int position, String status) {
        jdbc.update(
                "insert into event.queue_entry (id, event_id, name, name_normalized, position, status) "
                        + "values (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), eventId, name, normalized, position, status);
    }
}
