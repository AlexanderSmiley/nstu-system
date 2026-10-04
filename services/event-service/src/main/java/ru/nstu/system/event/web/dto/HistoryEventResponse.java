package ru.nstu.system.event.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;
import ru.nstu.system.event.domain.Event;
import ru.nstu.system.event.domain.EventStatus;

/**
 * One element of the event history ({@code GET /api/events/history}; task 9.7;
 * spec "Восстановление и безвозвратное удаление", scenario "Состав истории для
 * разных ролей").
 *
 * <p>{@code entryCount} is optional: it is the number of stored queue rows for a
 * {@code CLOSED} event and {@code null} for an {@code ARCHIVED} one, whose rows
 * live compressed in {@code archive_payload}. The explicit accessor marks it
 * {@link JsonInclude.Include#NON_NULL}, so it is omitted rather than {@code null};
 * the other (legitimately-null) timestamps stay in the payload.</p>
 *
 * @param entryCount stored queue rows, or {@code null} for an archived event
 */
public record HistoryEventResponse(
        UUID id,
        String title,
        String slug,
        EventStatus status,
        Instant startsAt,
        Instant closedAt,
        Instant archivedAt,
        int retentionDays,
        Long entryCount) {

    /** @return the wire projection of a history row */
    public static HistoryEventResponse from(Event event, Long entryCount) {
        return new HistoryEventResponse(
                event.getId(),
                event.getTitle(),
                event.getSlug(),
                event.getStatus(),
                event.getStartsAt(),
                event.getClosedAt(),
                event.getArchivedAt(),
                event.getRetentionDays(),
                entryCount);
    }

    /** Omits the optional count instead of emitting {@code "entryCount": null}. */
    @Override
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Long entryCount() {
        return entryCount;
    }
}
