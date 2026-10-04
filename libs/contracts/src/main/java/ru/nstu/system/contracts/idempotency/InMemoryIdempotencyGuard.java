package ru.nstu.system.contracts.idempotency;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-local {@link IdempotencyGuard}.
 *
 * <p>State lives only in the JVM that runs the consumer: it is lost on restart
 * and is not shared between instances. It is the sensible default for the
 * single-instance MVP, but a consumer that must survive restarts or scale out
 * has to switch to {@link JdbcIdempotencyGuard}. Re-delivery of an event that
 * was handled before a restart is therefore possible.</p>
 */
public class InMemoryIdempotencyGuard implements IdempotencyGuard {

    private final Set<UUID> processed = ConcurrentHashMap.newKeySet();

    @Override
    public boolean alreadyProcessed(UUID eventId) {
        return processed.contains(require(eventId));
    }

    @Override
    public void markProcessed(UUID eventId) {
        processed.add(require(eventId));
    }

    /** Number of tracked event ids; mainly useful for diagnostics and tests. */
    public int size() {
        return processed.size();
    }

    /** Forgets all tracked ids; mainly useful for tests. */
    public void clear() {
        processed.clear();
    }

    private UUID require(UUID eventId) {
        return Objects.requireNonNull(eventId, "eventId must not be null");
    }
}
