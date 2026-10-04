package ru.nstu.system.contracts.idempotency;

import java.util.UUID;

/**
 * Tracks which events a consumer has already handled (design.md D13).
 *
 * <p>Two implementations are provided: {@link InMemoryIdempotencyGuard} for
 * single-instance deployments and tests, and {@link JdbcIdempotencyGuard} for
 * durable, cross-instance deduplication.</p>
 */
public interface IdempotencyGuard {

    /** @return {@code true} if the event was already successfully handled */
    boolean alreadyProcessed(UUID eventId);

    /** Records the event as handled; must be idempotent by itself. */
    void markProcessed(UUID eventId);
}
