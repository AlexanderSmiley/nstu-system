package ru.nstu.system.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.nstu.system.contracts.events.AccountBlockedPayload;
import ru.nstu.system.contracts.events.AccountCreatedPayload;
import ru.nstu.system.contracts.events.AccountPasswordResetPayload;
import ru.nstu.system.contracts.events.AccountUnblockedPayload;
import ru.nstu.system.contracts.events.AccountUpdatedPayload;
import ru.nstu.system.contracts.events.DomainEvent;
import ru.nstu.system.contracts.events.EntryPassedPayload;
import ru.nstu.system.contracts.events.EventArchivedPayload;
import ru.nstu.system.contracts.events.EventClosedPayload;
import ru.nstu.system.contracts.events.EventTypes;
import ru.nstu.system.contracts.events.ProfileUpdatedPayload;
import ru.nstu.system.contracts.events.QueueAdvancedPayload;
import ru.nstu.system.contracts.messaging.NstuQueueNames;
import ru.nstu.system.contracts.messaging.RabbitNames;
import ru.nstu.system.notification.config.NotificationMessagingConfig;

/**
 * RabbitMQ-backed tests of the {@code notification-service} stub (tasks 10.1,
 * 10.2): every domain event type is received and handled, redelivery is
 * idempotent, and a poisoned message is dead-lettered without blocking the next
 * one, then recovered by the re-drive scheduler.
 */
class NotificationMessagingIntegrationTest extends AbstractNotificationIntegrationTest {

    @Test
    void receivesAndProcessesEveryDomainEventType() {
        List<DomainEvent<?>> events = allDomainEvents();

        events.forEach(eventPublisher::publish);

        await().atMost(awaitTimeout())
                .until(() -> events.stream()
                        .allMatch(event -> idempotencyGuard.alreadyProcessed(event.eventId())));
        assertThat(events).allSatisfy(event ->
                assertThat(testEventHandler.invocationCount(event.eventId())).isEqualTo(1));
    }

    @Test
    void redeliveredEventIsHandledOnlyOnce() {
        DomainEvent<?> event = accountCreated();
        DomainEvent<?> sentinel = accountCreated();

        eventPublisher.publish(event);
        eventPublisher.publish(event);
        eventPublisher.publish(sentinel);

        // The sentinel is processed after the duplicates (single consumer), so
        // observing it guarantees the duplicates were handled.
        await().atMost(awaitTimeout())
                .until(() -> idempotencyGuard.alreadyProcessed(sentinel.eventId()));
        assertThat(testEventHandler.invocationCount(event.eventId())).isEqualTo(1);
        assertThat(idempotencyGuard.alreadyProcessed(event.eventId())).isTrue();
    }

    @Test
    void poisonMessageIsDeadLetteredAndRecoveredByReDrive() {
        DomainEvent<?> poison = poisonEvent();
        DomainEvent<?> good = accountCreated();

        testEventHandler.poison(poison.eventId());
        eventPublisher.publish(poison);
        eventPublisher.publish(good);

        // A failing message must not block the following one.
        await().atMost(awaitTimeout())
                .until(() -> idempotencyGuard.alreadyProcessed(good.eventId()));
        await().atMost(awaitTimeout())
                .until(() -> queueMessageCount(RabbitNames.DLQ) >= 1);
        // A failure must not mark the event processed, otherwise a replay would be skipped.
        assertThat(idempotencyGuard.alreadyProcessed(poison.eventId())).isFalse();

        testEventHandler.heal();
        int moved = dlqReDriveScheduler.reDrive();
        assertThat(moved).isGreaterThanOrEqualTo(1);

        await().atMost(awaitTimeout())
                .until(() -> idempotencyGuard.alreadyProcessed(poison.eventId()));
        assertThat(testEventHandler.invocationCount(poison.eventId())).isGreaterThanOrEqualTo(2);
    }

    @Test
    void queueNameMatchesSharedConvention() {
        assertThat(NotificationMessagingConfig.NOTIFICATION_EVENTS_QUEUE)
                .isEqualTo(NstuQueueNames.forService(NotificationMessagingConfig.SERVICE_NAME));
    }

    // ------------------------------------------------------------------
    // Event fixtures
    // ------------------------------------------------------------------

    private static DomainEvent<?> accountCreated() {
        return DomainEvent.of(EventTypes.ACCOUNT_CREATED, new AccountCreatedPayload(
                UUID.randomUUID(), "student", "STUDENT", "Иванов Иван Иванович"));
    }

    private static DomainEvent<?> poisonEvent() {
        return new DomainEvent<>(
                UUID.randomUUID(),
                "test.poison",
                EventTypes.CURRENT_VERSION,
                Instant.now(),
                new AccountCreatedPayload(UUID.randomUUID(), "poison", "STUDENT", "Отравленное сообщение"));
    }

    /** One event of every type in {@link EventTypes#ALL}. */
    private static List<DomainEvent<?>> allDomainEvents() {
        UUID accountId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        UUID nextEntryId = UUID.randomUUID();
        Instant now = Instant.now();

        return List.of(
                DomainEvent.of(EventTypes.ACCOUNT_CREATED, new AccountCreatedPayload(
                        accountId, "student", "STUDENT", "Иванов Иван Иванович")),
                DomainEvent.of(EventTypes.ACCOUNT_UPDATED, new AccountUpdatedPayload(
                        accountId, "student", "STAFF", "Иванов Иван Иванович")),
                DomainEvent.of(EventTypes.ACCOUNT_BLOCKED, new AccountBlockedPayload(accountId)),
                DomainEvent.of(EventTypes.ACCOUNT_UNBLOCKED, new AccountUnblockedPayload(accountId)),
                DomainEvent.of(EventTypes.ACCOUNT_PASSWORD_RESET, new AccountPasswordResetPayload(accountId)),
                DomainEvent.of(EventTypes.PROFILE_UPDATED, new ProfileUpdatedPayload(
                        accountId, "Петров Пётр Петрович")),
                DomainEvent.of(EventTypes.EVENT_CLOSED, new EventClosedPayload(
                        eventId, "lab-1", "Лабораторная №1")),
                DomainEvent.of(EventTypes.ENTRY_PASSED, new EntryPassedPayload(
                        eventId, entryId, "Бригада 1", now, accountId)),
                DomainEvent.of(EventTypes.QUEUE_ADVANCED, new QueueAdvancedPayload(
                        eventId, entryId, "Бригада 1", nextEntryId)),
                DomainEvent.of(EventTypes.EVENT_ARCHIVED, new EventArchivedPayload(eventId)));
    }
}
