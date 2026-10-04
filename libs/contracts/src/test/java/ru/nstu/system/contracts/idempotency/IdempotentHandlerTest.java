package ru.nstu.system.contracts.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import ru.nstu.system.contracts.events.AccountBlockedPayload;
import ru.nstu.system.contracts.events.DomainEvent;
import ru.nstu.system.contracts.events.EventTypes;

/**
 * Unit tests for {@link IdempotentHandler}: exactly-once action execution and
 * rejection (not requeue) of failing events.
 */
class IdempotentHandlerTest {

    private static DomainEvent<AccountBlockedPayload> event(UUID id) {
        return new DomainEvent<>(
                id, EventTypes.ACCOUNT_BLOCKED, 1, Instant.now(), new AccountBlockedPayload(UUID.randomUUID()));
    }

    @Test
    void runsActionOnceForRepeatedDelivery() {
        InMemoryIdempotencyGuard guard = new InMemoryIdempotencyGuard();
        IdempotentHandler handler = new IdempotentHandler(guard);
        UUID eventId = UUID.randomUUID();
        DomainEvent<AccountBlockedPayload> domainEvent = event(eventId);
        AtomicInteger executions = new AtomicInteger();

        handler.handle(domainEvent, executions::incrementAndGet);
        handler.handle(domainEvent, executions::incrementAndGet);
        handler.handle(domainEvent, executions::incrementAndGet);

        assertThat(executions.get()).isEqualTo(1);
        assertThat(guard.alreadyProcessed(eventId)).isTrue();
    }

    @Test
    void failingActionIsRejectedToDeadLetterAndNotMarkedProcessed() {
        InMemoryIdempotencyGuard guard = new InMemoryIdempotencyGuard();
        IdempotentHandler handler = new IdempotentHandler(guard);
        UUID eventId = UUID.randomUUID();

        assertThatThrownBy(() -> handler.handle(event(eventId), () -> {
            throw new IllegalStateException("poison");
        }))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasCauseInstanceOf(IllegalStateException.class);

        assertThat(guard.alreadyProcessed(eventId)).isFalse();
    }

    @Test
    void marksProcessedAfterSuccessfulAction() {
        InMemoryIdempotencyGuard guard = new InMemoryIdempotencyGuard();
        IdempotentHandler handler = new IdempotentHandler(guard);
        UUID eventId = UUID.randomUUID();

        handler.handle(event(eventId), () -> {
        });

        assertThat(guard.alreadyProcessed(eventId)).isTrue();
        assertThat(guard.size()).isEqualTo(1);
    }
}
