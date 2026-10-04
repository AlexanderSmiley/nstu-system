package ru.nstu.system.event.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import ru.nstu.system.event.domain.Availability;
import ru.nstu.system.event.domain.EntryUnit;
import ru.nstu.system.event.domain.Event;
import ru.nstu.system.event.domain.EventStatus;
import ru.nstu.system.event.domain.JournalVisibility;

/**
 * Representation of a single event returned by {@code GET /api/events/{id}}
 * (task 9.1; spec "Журнал сдач").
 *
 * <p>It carries the same event fields as {@link EventResponse} and adds the
 * surrender journal as a list of {@link QueueEntryResponse} — the exact element
 * DTO returned by {@code GET /api/events/{id}/queue}. The two endpoints share the
 * journal query through {@code QueueProjection} instead of duplicating it.</p>
 *
 * <p>{@code journal} is {@code null} when the caller may not see it (not
 * staff/admin and {@code journalVisibility = STAFF}); the explicit accessor marks
 * the property {@link JsonInclude.Include#NON_NULL} so the key is <em>absent</em>
 * from the JSON rather than {@code null}. The annotation is placed on the accessor
 * rather than on the record so that legitimately-null event fields
 * ({@code description}, {@code startsAt}) keep being serialised as {@code null},
 * preserving the existing {@link EventResponse} shape.</p>
 *
 * @param journal passed entries by surrender time, or {@code null} when hidden
 */
public record EventDetailResponse(
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
        Instant updatedAt,
        List<QueueEntryResponse> journal) {

    /** @return the wire projection of an event together with its journal (may be {@code null}) */
    public static EventDetailResponse from(Event event, List<QueueEntryResponse> journal) {
        return new EventDetailResponse(
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
                event.getUpdatedAt(),
                journal);
    }

    /** Keeps the journal key out of the JSON entirely when it is hidden. */
    @Override
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public List<QueueEntryResponse> journal() {
        return journal;
    }
}
