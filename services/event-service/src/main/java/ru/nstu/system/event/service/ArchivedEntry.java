package ru.nstu.system.event.service;

import java.time.Instant;
import java.util.UUID;
import ru.nstu.system.event.domain.QueueEntry;
import ru.nstu.system.event.domain.QueueEntryStatus;
import ru.nstu.system.event.domain.QueueOrigin;

/**
 * One {@code queue_entry} row as stored inside {@code event.archive_payload}
 * (design.md D19; task 9.6).
 *
 * <p>The snapshot preserves every column, including the historical
 * {@code position}, the {@code created_at} timestamp and the surrender metadata,
 * so a restore reconstructs the queue and journal exactly as they were archived.
 * The type is a Jackson-friendly Java record, serialised with the shared
 * {@code EventJson} mapper (ISO-8601 instants, enum codes).</p>
 */
public record ArchivedEntry(
        UUID id,
        String name,
        String nameNormalized,
        int position,
        QueueEntryStatus status,
        UUID holderAccountId,
        UUID guestRef,
        QueueOrigin origin,
        Instant createdAt,
        Instant passedAt,
        UUID passedBy) {

    /** @return the snapshot of a live entry */
    public static ArchivedEntry from(QueueEntry entry) {
        return new ArchivedEntry(
                entry.getId(),
                entry.getName(),
                entry.getNameNormalized(),
                entry.getPosition(),
                entry.getStatus(),
                entry.getHolderAccountId(),
                entry.getGuestRef(),
                entry.getOrigin(),
                entry.getCreatedAt(),
                entry.getPassedAt(),
                entry.getPassedBy());
    }

    /** Rebuilds a persisted entry for the given event from this snapshot. */
    public QueueEntry toEntity(UUID eventId) {
        return QueueEntry.restored(
                id,
                eventId,
                name,
                nameNormalized,
                position,
                status,
                holderAccountId,
                guestRef,
                origin,
                createdAt,
                passedAt,
                passedBy);
    }
}
