package ru.nstu.system.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.nstu.system.auth.domain.AccountRepository;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies the {@code auth} Flyway migrations against a throwaway PostgreSQL
 * container: schema is created from scratch, all objects exist and a repeated
 * migration run is a no-op.
 *
 * <p>The administrator credentials are intentionally blank, which also covers
 * the "no administrator configured" startup path.</p>
 */
@Testcontainers
@SpringBootTest(properties = {
        "admin.username=",
        "admin.password=",
        "nstu.jwt.secret=nstu-integration-test-secret-0123456789",
        // No outbox rows are produced here; keep the scheduler off RabbitMQ (none runs).
        "nstu.outbox.poll-interval=PT24H"
})
class AuthSchemaMigrationTest {

    // currentSchema mirrors docker-compose: the schema does not exist yet, so it
    // must be created by Flyway (spring.flyway.create-schemas).
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withUrlParam("currentSchema", "auth");

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Flyway flyway;

    @Autowired
    private AccountRepository accountRepository;

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
    }

    @AfterEach
    void cleanUp() {
        // Keep tests independent: several of them insert rows directly.
        jdbc.update("delete from auth.refresh_token");
        jdbc.update("delete from auth.account");
    }

    @Test
    void createsAllAuthTablesInDedicatedSchema() {
        List<String> tables = jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'auth'",
                String.class);

        assertThat(tables)
                .contains("account", "refresh_token", "outbox", "site_setting", "flyway_schema_history");
    }

    @Test
    void createsIndexesForAccountLookupsAndOutboxPolling() {
        List<String> indexes = jdbc.queryForList(
                "select indexname from pg_indexes where schemaname = 'auth'",
                String.class);

        assertThat(indexes).contains(
                "account_username_uniq",
                "account_username_normalized_uniq",
                "account_single_admin_uniq",
                "refresh_token_account_id_idx",
                "refresh_token_token_hash_idx",
                "outbox_published_at_idx");
    }

    @Test
    void seedsDefaultSiteName() {
        String siteName = jdbc.queryForObject(
                "select value #>> '{}' from auth.site_setting where key = 'site.name'",
                String.class);

        assertThat(siteName).isEqualTo("NSTU System");
    }

    @Test
    void roleCheckConstraintRejectsGuest() {
        assertThatThrownBy(() -> jdbc.update(
                "insert into auth.account (id, username, username_normalized, password_hash, role) "
                        + "values (?, ?, ?, ?, ?)",
                UUID.randomUUID(), "ghost", "ghost", "$2a$10$hash", "GUEST"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void secondAdminInsertIsRejectedByPartialUniqueIndex() {
        insertAccount("root", "root", "ADMIN");

        assertThatThrownBy(() -> insertAccount("root-two", "root-two", "ADMIN"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(ex -> assertThat(sqlStateOf(ex)).isEqualTo("23505"));

        assertThat(jdbc.queryForObject(
                "select count(*) from auth.account where role = 'ADMIN'", Integer.class)).isEqualTo(1);
    }

    @Test
    void severalNonAdminAccountsAreAllowed() {
        // The index is partial (role = 'ADMIN'): STAFF/STUDENT rows must not be restricted.
        insertAccount("staff", "staff", "STAFF");
        insertAccount("student", "student", "STUDENT");

        assertThat(jdbc.queryForObject(
                "select count(*) from auth.account where role <> 'ADMIN'", Integer.class)).isEqualTo(2);
    }

    @Test
    void duplicateRefreshTokenHashIsRejected() {
        UUID accountId = insertAccount("student", "student", "STUDENT");
        insertRefreshToken(accountId, "same-hash");

        assertThatThrownBy(() -> insertRefreshToken(accountId, "same-hash"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(ex -> assertThat(sqlStateOf(ex)).isEqualTo("23505"));

        assertThat(jdbc.queryForObject(
                "select count(*) from auth.refresh_token where token_hash = 'same-hash'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void repeatedMigrationRunIsNoOp() {
        var result = flyway.migrate();

        assertThat(result.migrationsExecuted).isZero();
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    void startsWithoutAdministratorWhenCredentialsAreMissing() {
        assertThat(accountRepository.count()).isZero();
    }

    private UUID insertAccount(String username, String usernameNormalized, String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into auth.account "
                        + "(id, username, username_normalized, password_hash, role) values (?, ?, ?, ?, ?)",
                id, username, usernameNormalized, "$2a$10$hash", role);
        return id;
    }

    private void insertRefreshToken(UUID accountId, String tokenHash) {
        jdbc.update("insert into auth.refresh_token (id, account_id, token_hash, expires_at) "
                        + "values (?, ?, ?, now() + interval '1 hour')",
                UUID.randomUUID(), accountId, tokenHash);
    }

    private static String sqlStateOf(Throwable throwable) {
        Throwable cause = throwable;
        while (cause != null) {
            if (cause instanceof SQLException sqlException) {
                return sqlException.getSQLState();
            }
            cause = cause.getCause();
        }
        return null;
    }
}
