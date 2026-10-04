package ru.nstu.system.student;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import ru.nstu.system.contracts.Groups;
import ru.nstu.system.contracts.events.AccountCreatedPayload;
import ru.nstu.system.contracts.events.DomainEvent;
import ru.nstu.system.contracts.events.EventTypes;
import ru.nstu.system.contracts.messaging.EventPublisher;
import ru.nstu.system.contracts.messaging.RabbitNames;
import ru.nstu.system.security.RoleNames;
import ru.nstu.system.security.TokenIssuer;
import ru.nstu.system.student.config.StudentMessagingConfig;
import ru.nstu.system.student.domain.StudentProfileRepository;
import ru.nstu.system.student.messaging.DlqReDriveScheduler;

/**
 * Shared setup for the {@code student-service} integration tests (tasks 6.1-6.4).
 *
 * <p>One Spring context, one PostgreSQL 16 container and one RabbitMQ broker are
 * reused by all subclasses (identical dynamic properties keep Boot's context cache
 * effective). Tests are not transactional: the listener works in its own
 * transactions, so state is reset explicitly before every test.</p>
 *
 * <p>The listener retry is limited to a single attempt so that a failure lands in
 * the DLQ immediately; the DLQ re-drive schedule itself is pushed far away and the
 * tests call {@link DlqReDriveScheduler#reDrive()} explicitly.</p>
 */
@SpringBootTest(properties = {
        "nstu.jwt.secret=nstu-integration-test-secret-0123456789",
        "nstu.internal.token=test-internal-token",
        "nstu.outbox.poll-interval=PT1H",
        "nstu.dlq.redrive-interval=PT24H",
        "spring.rabbitmq.listener.simple.retry.enabled=true",
        "spring.rabbitmq.listener.simple.retry.max-attempts=1",
        "spring.rabbitmq.listener.simple.retry.initial-interval=10ms"
})
@AutoConfigureMockMvc
abstract class AbstractStudentIntegrationTest {

    static final String INTERNAL_TOKEN = "test-internal-token";

    static final String AWAY_FULL_NAME = "Иванов Иван Иванович";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected TokenIssuer tokenIssuer;

    @Autowired
    protected EventPublisher eventPublisher;

    @Autowired
    protected AmqpAdmin amqpAdmin;

    @Autowired
    protected RabbitTemplate rabbitTemplate;

    @Autowired
    protected StudentProfileRepository profileRepository;

    @Autowired
    protected DlqReDriveScheduler dlqReDriveScheduler;

    @Autowired
    protected PlatformTransactionManager transactionManager;

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", StudentTestContainers.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", StudentTestContainers.POSTGRES::getUsername);
        registry.add("spring.datasource.password", StudentTestContainers.POSTGRES::getPassword);
        registry.add("spring.rabbitmq.host", StudentTestContainers.RABBIT::getHost);
        registry.add("spring.rabbitmq.port", StudentTestContainers.RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", StudentTestContainers.RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", StudentTestContainers.RABBIT::getAdminPassword);
    }

    @BeforeEach
    void resetState() {
        jdbcTemplate.update("delete from student.student_profile");
        jdbcTemplate.update("delete from student.outbox");
        jdbcTemplate.update("delete from student.processed_event");
        // Upsert, not "do nothing": a test may rename the seeded group, and the
        // name must be restored so the following tests are order-independent.
        jdbcTemplate.update(
                "insert into student.app_group (id, name) values (?, ?) "
                        + "on conflict (id) do update set name = excluded.name",
                Groups.DEFAULT_GROUP_ID, "НГТУ — группа по умолчанию");
        purgeQueue(RabbitNames.DLQ);
        purgeQueue(StudentMessagingConfig.STUDENT_EVENTS_QUEUE);
    }

    // ------------------------------------------------------------------
    // Domain helpers
    // ------------------------------------------------------------------

    protected UUID insertProfile(String fullName) {
        UUID accountId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into student.student_profile (id, full_name, group_id) values (?, ?, ?)",
                accountId, fullName, Groups.DEFAULT_GROUP_ID);
        return accountId;
    }

    protected DomainEvent<AccountCreatedPayload> accountCreatedEvent(
            UUID eventId, UUID accountId, String role, String displayName) {
        return new DomainEvent<>(
                eventId,
                EventTypes.ACCOUNT_CREATED,
                EventTypes.CURRENT_VERSION,
                Instant.now(),
                new AccountCreatedPayload(
                        accountId,
                        "user-" + accountId.toString().substring(0, 8),
                        role,
                        displayName));
    }

    protected void publishAccountCreated(UUID eventId, UUID accountId, String role, String displayName) {
        eventPublisher.publish(accountCreatedEvent(eventId, accountId, role, displayName));
    }

    protected boolean profileExists(UUID accountId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from student.student_profile where id = ?", Integer.class, accountId);
        return count != null && count > 0;
    }

    protected String profileFullName(UUID accountId) {
        var names = jdbcTemplate.queryForList(
                "select full_name from student.student_profile where id = ?", String.class, accountId);
        return names.isEmpty() ? null : names.get(0);
    }

    protected int profileCount(UUID accountId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from student.student_profile where id = ?", Integer.class, accountId);
        return count == null ? 0 : count;
    }

    protected int processedCount(UUID eventId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from student.processed_event where event_id = ?", Integer.class, eventId);
        return count == null ? 0 : count;
    }

    protected int outboxCount() {
        Integer count = jdbcTemplate.queryForObject("select count(*) from student.outbox", Integer.class);
        return count == null ? 0 : count;
    }

    protected int queueMessageCount(String queue) {
        var info = amqpAdmin.getQueueInfo(queue);
        return info == null ? 0 : info.getMessageCount();
    }

    // ------------------------------------------------------------------
    // Token helpers
    // ------------------------------------------------------------------

    protected String tokenFor(UUID accountId, String role) {
        return tokenIssuer.issueAccessToken(accountId.toString(), Set.of(role), false);
    }

    protected String studentToken(UUID accountId) {
        return tokenFor(accountId, RoleNames.STUDENT);
    }

    protected String staffToken(UUID accountId) {
        return tokenFor(accountId, RoleNames.STAFF);
    }

    protected String adminToken(UUID accountId) {
        return tokenFor(accountId, RoleNames.ADMIN);
    }

    protected String guestToken() {
        return tokenIssuer.issueAccessToken("guest:" + UUID.randomUUID(), Set.of(RoleNames.GUEST), false);
    }

    protected static Duration awaitTimeout() {
        return Duration.ofSeconds(30);
    }

    private void purgeQueue(String queue) {
        try {
            amqpAdmin.purgeQueue(queue);
        } catch (RuntimeException ex) {
            // The queue may legitimately not be declared yet on the very first
            // run; tests publish afterwards, so this is safe to ignore.
        }
    }
}
