package ru.nstu.system.event.web.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import ru.nstu.system.event.domain.Audience;
import ru.nstu.system.event.domain.CalendarEntry;

/**
 * Wire projection of a calendar entry (change add-calendar-module; design.md D4).
 *
 * <p>Dates are emitted as plain ISO strings ({@code 2026-10-04},
 * {@code 14:30}) so the client never has to reinterpret them through a time
 * zone. {@code mine} is {@code true} when the caller is the author; it drives the
 * edit/delete actions without leaking the author's identity comparison to the
 * client. {@code authorDisplayName} is the name snapshotted at creation
 * (change add-preferences-and-calendar-ui; design.md D5) and may be {@code null}
 * when it could not be resolved.</p>
 */
public record CalendarEntryResponse(
        UUID id,
        String title,
        String description,
        LocalDate startsOn,
        LocalTime startsAt,
        Audience audience,
        UUID authorAccountId,
        String authorDisplayName,
        boolean mine) {

    /** @return the wire projection as seen by {@code viewerId} */
    public static CalendarEntryResponse from(CalendarEntry entry, UUID viewerId) {
        return new CalendarEntryResponse(
                entry.getId(),
                entry.getTitle(),
                entry.getDescription(),
                entry.getStartsOn(),
                entry.getStartsAt(),
                entry.getAudience(),
                entry.getAuthorAccountId(),
                entry.getAuthorDisplayName(),
                entry.getAuthorAccountId().equals(viewerId));
    }
}
