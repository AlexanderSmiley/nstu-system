package ru.nstu.system.event.web.dto;

import java.time.Instant;
import java.util.UUID;
import ru.nstu.system.event.domain.Availability;
import ru.nstu.system.event.domain.EntryUnit;
import ru.nstu.system.event.domain.Event;
import ru.nstu.system.event.domain.EventStatus;
import ru.nstu.system.event.domain.JournalVisibility;

/**
 * Representation of an event shared by create/get/list/by-slug (tasks 7.1-7.7).
 *
 * <p>Enum fields are rendered through their {@code @JsonValue} code, so the wire
 * form is {@code "GUEST+"}, {@code "BRIGADE"} and so on. The DTO deliberately
 * carries no queue or journal state: the queue is served by a dedicated endpoint
 * in group 8, and the by-slug response must never expose it (task 7.4).</p>
 */
public record EventResponse(
        UUID id,
        String title,
        String description,
        Availability availability,
        Instant startsAt,
        int entryLimit,
        EntryUnit entryUnit,
        JournalVisibility journalVisibility,
        int retentionDays,
        String slug,
        EventStatus status,
        UUID groupId,
        Instant createdAt,
        Instant updatedAt) {

    /** @return the wire projection of a persisted event */
    public static EventResponse from(Event event) {
        return new EventResponse(
                event.getId(),
                event.getTitle(),
                event.getDescription(),
                event.getAvailability(),
                event.getStartsAt(),
                event.getEntryLimit(),
                event.getEntryUnit(),
                event.getJournalVisibility(),
                event.getRetentionDays(),
                event.getSlug(),
                event.getStatus(),
                event.getGroupId(),
                event.getCreatedAt(),
                event.getUpdatedAt());
    }
}
