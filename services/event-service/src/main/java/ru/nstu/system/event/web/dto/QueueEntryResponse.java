package ru.nstu.system.event.web.dto;

import java.time.Instant;
import java.util.UUID;
import ru.nstu.system.event.domain.QueueEntry;
import ru.nstu.system.event.domain.QueueEntryStatus;
import ru.nstu.system.event.domain.QueueOrigin;

/**
 * Representation of one queue entry in the queue/journal projection
 * {@code GET /api/events/{id}/queue} (spec "Порядок очереди").
 *
 * @param id              entry identifier
 * @param position        1-based position among active entries; historical for passed rows
 * @param name            display-name snapshot captured on joining
 * @param status          {@code WAITING}/{@code PAUSED}/{@code PASSED}
 * @param origin          how the entry was created; {@code JOIN} for this group
 * @param holderAccountId account of a student/staff participant, or {@code null}
 * @param guestRef        guest session identifier, or {@code null}
 * @param passedAt        surrender time for {@code PASSED} rows, otherwise {@code null}
 * @param createdAt       join time
 */
public record QueueEntryResponse(
        UUID id,
        int position,
        String name,
        QueueEntryStatus status,
        QueueOrigin origin,
        UUID holderAccountId,
        UUID guestRef,
        Instant passedAt,
        Instant createdAt) {

    /** @return the wire projection of a persisted entry */
    public static QueueEntryResponse from(QueueEntry entry) {
        return new QueueEntryResponse(
                entry.getId(),
                entry.getPosition(),
                entry.getName(),
                entry.getStatus(),
                entry.getOrigin(),
                entry.getHolderAccountId(),
                entry.getGuestRef(),
                entry.getPassedAt(),
                entry.getCreatedAt());
    }
}
