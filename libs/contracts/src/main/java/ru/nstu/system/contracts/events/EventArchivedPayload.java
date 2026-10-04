package ru.nstu.system.contracts.events;

import java.util.UUID;

/**
 * Payload of {@link EventTypes#EVENT_ARCHIVED}.
 *
 * @param eventId archived event identifier
 */
public record EventArchivedPayload(UUID eventId) {
}
