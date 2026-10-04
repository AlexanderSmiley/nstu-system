package ru.nstu.system.contracts.events;

import java.util.UUID;

/**
 * Payload of {@link EventTypes#EVENT_CLOSED}.
 *
 * @param eventId closed event identifier
 * @param slug    stable short link slug
 * @param title   human-readable event title
 */
public record EventClosedPayload(UUID eventId, String slug, String title) {
}
