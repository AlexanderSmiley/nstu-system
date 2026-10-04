package ru.nstu.system.student;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.nstu.system.contracts.Groups;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies the {@code student} Flyway migrations against a throwaway PostgreSQL
 * container: schema creation from scratch, the seeded default group and the
 * idempotency of a repeated migration run.
 */
@Testcontainers
@SpringBootTest(properties = {
        // The full application context now includes the security and messaging
        // configuration, so the JWT secret must be present (HS256, >= 32 bytes).
        "nstu.jwt.secret=nstu-integration-test-secret-0123456789",
        // No broker is available here; never start the listener containers.
        "spring.rabbitmq.listener.simple.auto-startup=false",
        // Keep the DLQ scheduler out of the way (it tolerates an absent broker
        // anyway, but this makes the test silent and deterministic).
        "nstu.dlq.redrive-interval=PT24H"
})
class StudentSchemaMigrationTest {

    // currentSchema mirrors docker-compose: the schema does not exist yet, so it
    // must be created by Flyway (spring.flyway.create-schemas).
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withUrlParam("currentSchema", "student");

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
    void createsAllStudentTablesInDedicatedSchema() {
        List<String> tables = jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'student'",
                String.class);

        assertThat(tables)
                .contains("app_group", "student_profile", "outbox", "processed_event", "flyway_schema_history",
                        "note", "note_attachment", "profile_preferences");
    }

    @Test
    void studentProfileHasNoEmailColumn() {
        List<String> columns = jdbc.queryForList(
                "select column_name from information_schema.columns "
                        + "where table_schema = 'student' and table_name = 'student_profile'",
                String.class);

        // Customer decision: the email PII is stored only in auth.account.
        assertThat(columns).containsExactlyInAnyOrder(
                "id", "full_name", "group_id", "contacts", "created_at", "updated_at");
        assertThat(columns).doesNotContain("email");
    }

    @Test
    void processedEventHasTheSharedShape() {
        List<String> columns = jdbc.queryForList(
                "select column_name from information_schema.columns "
                        + "where table_schema = 'student' and table_name = 'processed_event'",
                String.class);

        assertThat(columns).containsExactlyInAnyOrder("event_id", "processed_at");
    }

    @Test
    void seedsDefaultGroupFromSharedContractConstant() {
        Integer count = jdbc.queryForObject(
                "select count(*) from student.app_group where id = ?",
                Integer.class,
                Groups.DEFAULT_GROUP_ID);

        assertThat(count).isEqualTo(1);
    }

    @Test
    void outboxHasTheSharedColumnShape() {
        List<String> columns = jdbc.queryForList(
                "select column_name from information_schema.columns "
                        + "where table_schema = 'student' and table_name = 'outbox'",
                String.class);

        // Canonical shape from libs/contracts db/outbox.sql: event_id is the PK,
        // there is no surrogate id column.
        assertThat(columns).containsExactlyInAnyOrder(
                "event_id", "event_type", "payload", "created_at", "published_at", "attempts");
    }

    @Test
    void createsOutboxPollingIndex() {
        List<String> indexes = jdbc.queryForList(
                "select indexname from pg_indexes where schemaname = 'student'",
                String.class);

        assertThat(indexes).contains(
                "outbox_pk",
                "outbox_published_at_idx",
                "student_profile_group_id_idx",
                "note_account_updated_idx",
                "note_attachment_note_id_idx");
    }

    @Test
    void studentProfileHasForeignKeyToDefaultGroup() {
        UUID profileId = UUID.randomUUID();
        jdbc.update(
                "insert into student.student_profile (id, full_name, group_id) values (?, ?, ?)",
                profileId, "Иванов Иван Иванович", Groups.DEFAULT_GROUP_ID);

        UUID groupId = jdbc.queryForObject(
                "select group_id from student.student_profile where id = ?",
                UUID.class,
                profileId);

        assertThat(groupId).isEqualTo(Groups.DEFAULT_GROUP_ID);
    }

    @Test
    void repeatedMigrationRunIsNoOp() {
        var result = flyway.migrate();

        assertThat(result.migrationsExecuted).isZero();
        assertThat(flyway.info().pending()).isEmpty();
    }

    // ------------------------------------------------------------------
    // Notes module (change add-notes-module)
    // ------------------------------------------------------------------

    @Test
    void noteHasExpectedColumnShape() {
        List<String> columns = jdbc.queryForList(
                "select column_name from information_schema.columns "
                        + "where table_schema = 'student' and table_name = 'note'",
                String.class);

        assertThat(columns).containsExactlyInAnyOrder(
                "id", "account_id", "title", "body", "created_at", "updated_at");
    }

    @Test
    void noteAttachmentHasExpectedColumnShape() {
        List<String> columns = jdbc.queryForList(
                "select column_name from information_schema.columns "
                        + "where table_schema = 'student' and table_name = 'note_attachment'",
                String.class);

        assertThat(columns).containsExactlyInAnyOrder(
                "id", "note_id", "file_name", "content_type", "size_bytes", "bytes", "created_at");
    }

    @Test
    void deletingANoteCascadesToItsAttachments() {
        UUID noteId = UUID.randomUUID();
        jdbc.update(
                "insert into student.note (id, account_id, title) values (?, ?, ?)",
                noteId, UUID.randomUUID(), "Заметка");
        jdbc.update(
                "insert into student.note_attachment"
                        + " (id, note_id, file_name, content_type, size_bytes, bytes)"
                        + " values (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), noteId, "file.txt", "text/plain", 3, new byte[] {1, 2, 3});

        jdbc.update("delete from student.note where id = ?", noteId);

        Integer attachments = jdbc.queryForObject(
                "select count(*) from student.note_attachment where note_id = ?",
                Integer.class,
                noteId);
        assertThat(attachments).isZero();
    }

    @Test
    void noteAttachmentRejectsUnknownNote() {
        // The foreign key must reject an attachment that references no note.
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(
                        "insert into student.note_attachment"
                                + " (id, note_id, file_name, content_type, size_bytes, bytes)"
                                + " values (?, ?, ?, ?, ?, ?)",
                        UUID.randomUUID(), UUID.randomUUID(), "file.txt", "text/plain", 1,
                        new byte[] {1}))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    // ------------------------------------------------------------------
    // Per-account preferences (change add-preferences-and-calendar-ui)
    // ------------------------------------------------------------------

    @Test
    void profilePreferencesHasExpectedColumnShape() {
        List<String> columns = jdbc.queryForList(
                "select column_name from information_schema.columns "
                        + "where table_schema = 'student' and table_name = 'profile_preferences'",
                String.class);

        assertThat(columns).containsExactlyInAnyOrder(
                "profile_id", "modules", "calendar_colors", "updated_at");
    }

    @Test
    void profilePreferencesDefaultsAreAppliedWhenOnlyTheProfileIsGiven() {
        UUID profileId = insertProfileRow();

        jdbc.update("insert into student.profile_preferences (profile_id) values (?)", profileId);

        String modules = jdbc.queryForObject(
                "select modules::text from student.profile_preferences where profile_id = ?",
                String.class, profileId);
        String colors = jdbc.queryForObject(
                "select calendar_colors::text from student.profile_preferences where profile_id = ?",
                String.class, profileId);

        assertThat(modules).contains("\"events\": true", "\"calendar\": true", "\"notes\": true");
        assertThat(colors).contains("\"ME\": \"#ffffff\"", "\"GROUP\": \"#cfe3ff\"", "\"STAFF\": \"#d9dde3\"");
    }

    @Test
    void deletingAProfileCascadesToItsPreferences() {
        UUID profileId = insertProfileRow();
        jdbc.update("insert into student.profile_preferences (profile_id) values (?)", profileId);

        jdbc.update("delete from student.student_profile where id = ?", profileId);

        Integer preferences = jdbc.queryForObject(
                "select count(*) from student.profile_preferences where profile_id = ?",
                Integer.class, profileId);
        assertThat(preferences).isZero();
    }

    private UUID insertProfileRow() {
        UUID profileId = UUID.randomUUID();
        jdbc.update(
                "insert into student.student_profile (id, full_name, group_id) values (?, ?, ?)",
                profileId, "Иванов Иван Иванович", Groups.DEFAULT_GROUP_ID);
        return profileId;
    }
}
