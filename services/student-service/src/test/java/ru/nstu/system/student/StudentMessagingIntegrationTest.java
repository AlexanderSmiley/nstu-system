package ru.nstu.system.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.nstu.system.contracts.Groups;
import ru.nstu.system.contracts.messaging.NstuQueueNames;
import ru.nstu.system.contracts.messaging.RabbitNames;
import ru.nstu.system.security.RoleNames;
import ru.nstu.system.student.config.StudentMessagingConfig;

/**
 * RabbitMQ-backed tests of the {@code account.created} flow (tasks 6.1, 6.2):
 * profile creation per role, idempotency on redelivery, and recovery from a
 * transient failure via the DLQ re-drive scheduler.
 */
class StudentMessagingIntegrationTest extends AbstractStudentIntegrationTest {

    @Test
    void createsProfileForStudentAccountCreated() {
        UUID eventId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        publishAccountCreated(eventId, accountId, RoleNames.STUDENT, "Студентов Студент Студентович");

        await().atMost(awaitTimeout()).until(() -> profileExists(accountId));
        assertThat(profileFullName(accountId)).isEqualTo("Студентов Студент Студентович");
        assertThat(processedCount(eventId)).isEqualTo(1);
    }

    @Test
    void createsProfileForStaffAccountCreated() {
        UUID accountId = UUID.randomUUID();

        publishAccountCreated(UUID.randomUUID(), accountId, RoleNames.STAFF, "Старостин Староста");

        await().atMost(awaitTimeout()).until(() -> profileExists(accountId));
        assertThat(profileFullName(accountId)).isEqualTo("Старостин Староста");
    }

    @Test
    void createsProfileForAdminAccountCreated() {
        UUID eventId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        publishAccountCreated(eventId, accountId, RoleNames.ADMIN, "Админов Админ");

        // Every account owns a profile, including ADMIN (change
        // add-preferences-and-calendar-ui, identity spec "Профиль у всех
        // аккаунтов, включая администратора").
        await().atMost(awaitTimeout()).until(() -> profileExists(accountId));
        assertThat(profileFullName(accountId)).isEqualTo("Админов Админ");
        assertThat(processedCount(eventId)).isEqualTo(1);
    }

    @Test
    void redeliveredEventCreatesExactlyOneProfile() {
        UUID eventId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID sentinelAccount = UUID.randomUUID();

        publishAccountCreated(eventId, accountId, RoleNames.STUDENT, "Дубликатов Дубль");
        publishAccountCreated(eventId, accountId, RoleNames.STUDENT, "Дубликатов Дубль");
        publishAccountCreated(eventId, accountId, RoleNames.STUDENT, "Дубликатов Дубль");
        // The sentinel is handled after the duplicates (single consumer), so
        // observing it guarantees the duplicates have been consumed.
        publishAccountCreated(UUID.randomUUID(), sentinelAccount, RoleNames.STUDENT, "Контрольный Студент");

        await().atMost(awaitTimeout()).until(() -> profileExists(sentinelAccount));
        assertThat(profileCount(accountId)).isEqualTo(1);
        assertThat(processedCount(eventId)).isEqualTo(1);
    }

    @Test
    void failedProcessingIsDeadLetteredAndRecoveredByReDrive() {
        UUID eventId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        // Simulate a transient outage: the seeded default group disappears, so the
        // profile insert fails with a foreign-key violation. Re-inserting the group
        // is the "recovery"; the re-drive then replays the message.
        jdbcTemplate.update("delete from student.app_group where id = ?", Groups.DEFAULT_GROUP_ID);

        publishAccountCreated(eventId, accountId, RoleNames.STUDENT, "Восстановленный Студент");

        await().atMost(awaitTimeout())
                .until(() -> queueMessageCount(RabbitNames.DLQ) >= 1);
        assertThat(profileExists(accountId)).isFalse();
        // A failure must not mark the event processed, otherwise a replay would be skipped.
        assertThat(processedCount(eventId)).isZero();

        jdbcTemplate.update(
                "insert into student.app_group (id, name) values (?, ?) on conflict (id) do nothing",
                Groups.DEFAULT_GROUP_ID, "НГТУ — группа по умолчанию");

        int moved = dlqReDriveScheduler.reDrive();
        assertThat(moved).isGreaterThanOrEqualTo(1);

        await().atMost(awaitTimeout()).until(() -> profileExists(accountId));
        assertThat(profileFullName(accountId)).isEqualTo("Восстановленный Студент");
        assertThat(processedCount(eventId)).isEqualTo(1);
    }

    @Test
    void queueNameMatchesSharedConvention() {
        assertThat(StudentMessagingConfig.STUDENT_EVENTS_QUEUE)
                .isEqualTo(NstuQueueNames.forService(StudentMessagingConfig.SERVICE_NAME));
    }
}
