package ru.nstu.system.notification;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import ru.nstu.system.contracts.events.DomainEvent;
import ru.nstu.system.notification.messaging.NotificationEventHandler;

/**
 * Test double for the {@code notification-service} handler (task 10.2).
 *
 * <p>It delegates to the production {@code LoggingNotificationEventHandler} so
 * the real behaviour stays under test, and adds two test-only capabilities:
 * counting handler invocations per {@code eventId} (to prove exactly-once
 * handling) and failing deliberately for a chosen event (to exercise the DLQ).</p>
 */
class TestNotificationEventHandler implements NotificationEventHandler {

    private final NotificationEventHandler delegate;

    private final Map<UUID, AtomicInteger> invocations = new ConcurrentHashMap<>();

    private final AtomicReference<UUID> poison = new AtomicReference<>();

    TestNotificationEventHandler(NotificationEventHandler delegate) {
        this.delegate = delegate;
    }

    @Override
    public void handle(DomainEvent<?> event) {
        invocations.computeIfAbsent(event.eventId(), id -> new AtomicInteger()).incrementAndGet();
        if (event.eventId().equals(poison.get())) {
            throw new IllegalStateException("deliberate poison event " + event.eventId());
        }
        delegate.handle(event);
    }

    int invocationCount(UUID eventId) {
        AtomicInteger counter = invocations.get(eventId);
        return counter == null ? 0 : counter.get();
    }

    /** Makes the next delivery of {@code eventId} fail (dead-letter it). */
    void poison(UUID eventId) {
        poison.set(eventId);
    }

    /** Stops poisoning the configured event so the re-drive succeeds. */
    void heal() {
        poison.set(null);
    }

    void reset() {
        invocations.clear();
        poison.set(null);
    }
}
