package ru.nstu.system.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import ru.nstu.system.auth.bootstrap.AdminBootstrap;
import ru.nstu.system.auth.domain.Account;
import ru.nstu.system.auth.domain.AccountRepository;
import ru.nstu.system.auth.domain.Role;
import ru.nstu.system.contracts.events.EventTypes;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration tests for the administrator bootstrap (tasks 2.4/2.5).
 *
 * <p>Runs against a real PostgreSQL container so the credentials and BCrypt hash
 * are persisted exactly as in production. Each test is rolled back.</p>
 */
@Testcontainers
@SpringBootTest(properties = {
        "admin.username=admin",
        "admin.password=S3cret-pass",
        "nstu.jwt.secret=nstu-integration-test-secret-0123456789",
        // No outbox rows are produced here; keep the scheduler off RabbitMQ (none runs).
        "nstu.outbox.poll-interval=PT24H"
})
@Transactional
class AdminBootstrapIntegrationTest {

    // currentSchema mirrors docker-compose: the schema does not exist yet, so it
    // must be created by Flyway (spring.flyway.create-schemas).
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withUrlParam("currentSchema", "auth");

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AdminBootstrap adminBootstrap;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void firstStartCreatesExactlyOneAdminWithMandatoryPasswordChange() {
        // The ApplicationReadyEvent listener already ran during context startup.
        assertThat(accountRepository.countByRole(Role.ADMIN)).isEqualTo(1);

        Account admin = singleAdmin();
        assertThat(admin.getUsername()).isEqualTo("admin");
        assertThat(admin.getUsernameNormalized()).isEqualTo("admin");
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.isBlocked()).isFalse();
        assertThat(admin.isMustChangePassword()).isTrue();
        assertThat(admin.getPasswordHash()).startsWith("$2");
        assertThat(passwordEncoder.matches("S3cret-pass", admin.getPasswordHash())).isTrue();
    }

    @Test
    void repeatedBootstrapDoesNotDuplicateOrOverwritePassword() {
        String originalHash = singleAdmin().getPasswordHash();

        boolean createdAgain = adminBootstrap.bootstrap();

        assertThat(createdAgain).isFalse();
        assertThat(accountRepository.countByRole(Role.ADMIN)).isEqualTo(1);
        assertThat(singleAdmin().getPasswordHash()).isEqualTo(originalHash);
    }

    @Test
    void bootstrapCreatesAdminWhenNoneExists() {
        accountRepository.deleteAll();
        assertThat(accountRepository.count()).isZero();

        assertThat(adminBootstrap.bootstrap()).isTrue();
        assertThat(accountRepository.countByRole(Role.ADMIN)).isEqualTo(1);
    }

    @Test
    void bootstrapWritesExactlyOneAccountCreatedEvent() throws Exception {
        jdbcTemplate.update("delete from auth.outbox");
        accountRepository.deleteAll();

        assertThat(adminBootstrap.bootstrap()).isTrue();

        assertThat(outboxCountOfType(EventTypes.ACCOUNT_CREATED)).isEqualTo(1);
        String envelope = jdbcTemplate.queryForObject(
                "select payload::text from auth.outbox where event_type = ?",
                String.class, EventTypes.ACCOUNT_CREATED);

        Account admin = singleAdmin();
        JsonNode payload = objectMapper.readTree(envelope).get("payload");
        assertThat(UUID.fromString(payload.get("accountId").asText())).isEqualTo(admin.getId());
        assertThat(payload.get("username").asText()).isEqualTo("admin");
        assertThat(payload.get("role").asText()).isEqualTo(Role.ADMIN.name());
        assertThat(payload.get("displayName").asText()).isEqualTo("admin");
    }

    @Test
    void repeatedBootstrapDoesNotWriteSecondAccountCreatedEvent() {
        jdbcTemplate.update("delete from auth.outbox");
        accountRepository.deleteAll();

        assertThat(adminBootstrap.bootstrap()).isTrue();
        assertThat(adminBootstrap.bootstrap()).isFalse();

        assertThat(outboxCountOfType(EventTypes.ACCOUNT_CREATED)).isEqualTo(1);
    }

    private int outboxCountOfType(String eventType) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from auth.outbox where event_type = ?", Integer.class, eventType);
        return count == null ? 0 : count;
    }

    private Account singleAdmin() {
        return accountRepository.findAll().stream()
                .filter(account -> account.getRole() == Role.ADMIN)
                .findFirst()
                .orElseThrow(() -> new AssertionError("administrator account was not created"));
    }
}
