package ru.nstu.system.contracts.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Payload of {@link EventTypes#ENTRY_PASSED}.
 *
 * @param eventId  event identifier
 * @param entryId  queue entry that was marked as passed
 * @param name     entry name snapshot
 * @param passedAt instant the entry was marked as passed
 * @param passedBy staff account that pressed "next"
 */
public record EntryPassedPayload(
        UUID eventId,
        UUID entryId,
        String name,
        Instant passedAt,
        UUID passedBy) {
}
