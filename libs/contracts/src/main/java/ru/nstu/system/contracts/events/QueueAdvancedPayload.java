package ru.nstu.system.contracts.events;

import java.util.UUID;

/**
 * Payload of {@link EventTypes#QUEUE_ADVANCED}.
 *
 * @param eventId      event identifier
 * @param passedEntryId entry that left the waiting list
 * @param passedName   name snapshot of the passed entry
 * @param nextEntryId  entry that became the head of the queue, may be {@code null}
 */
public record QueueAdvancedPayload(
        UUID eventId,
        UUID passedEntryId,
        String passedName,
        UUID nextEntryId) {
}
