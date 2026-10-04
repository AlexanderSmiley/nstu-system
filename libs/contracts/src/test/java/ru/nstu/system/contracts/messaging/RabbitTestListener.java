package ru.nstu.system.contracts.messaging;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import ru.nstu.system.contracts.events.DomainEvent;
import ru.nstu.system.contracts.idempotency.IdempotentHandler;

/**
 * Test listener for {@link RabbitMessagingTest}: idempotently records events and
 * deliberately fails on {@link #POISON_EVENT_TYPE} to exercise DLQ routing.
 */
@Component
class RabbitTestListener {

    static final String POISON_EVENT_TYPE = "test.poison";

    private final IdempotentHandler handler;

    private final List<DomainEvent<?>> received = new CopyOnWriteArrayList<>();

    private final Set<UUID> processed = ConcurrentHashMap.newKeySet();

    private final Map<UUID, AtomicInteger> invocations = new ConcurrentHashMap<>();

    RabbitTestListener(IdempotentHandler handler) {
        this.handler = handler;
    }

    @RabbitListener(queues = RabbitMessagingTest.TEST_QUEUE)
    void onEvent(DomainEvent<?> event) {
        handler.handle(event, () -> {
            if (POISON_EVENT_TYPE.equals(event.eventType())) {
                throw new IllegalStateException("deliberate poison event " + event.eventId());
            }
            received.add(event);
            processed.add(event.eventId());
            invocations.computeIfAbsent(event.eventId(), id -> new AtomicInteger()).incrementAndGet();
        });
    }

    int invocationCount(UUID eventId) {
        AtomicInteger counter = invocations.get(eventId);
        return counter == null ? 0 : counter.get();
    }

    boolean processed(UUID eventId) {
        return processed.contains(eventId);
    }

    int receivedCount() {
        return received.size();
    }

    void reset() {
        received.clear();
        processed.clear();
        invocations.clear();
    }
}
