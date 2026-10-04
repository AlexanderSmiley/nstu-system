package ru.nstu.system.event.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import ru.nstu.system.event.domain.Availability;
import ru.nstu.system.event.domain.EntryUnit;
import ru.nstu.system.event.domain.EventType;
import ru.nstu.system.event.domain.JournalVisibility;

/**
 * Body of {@code POST /api/events} (tasks 7.1, 7.2).
 *
 * <p>Only {@code title} is required. Every optional field falls back to the
 * documented default when omitted. Enum-typed fields reject unknown codes during
 * JSON binding, so a {@code type} other than {@code QUEUE} (or an unknown
 * availability/unit/visibility) is answered with 400 without extra validation.</p>
 *
 * @param title             event name, non-blank, at most 200 characters
 * @param description       optional free text
 * @param availability      access level; default {@code GUEST+}
 * @param startsAt          optional ISO-8601 start time
 * @param entryLimit        limit in {@code 1..1000}; default 27, validated in
 *                          {@link ru.nstu.system.event.service.EventService}
 * @param entryUnit         {@code BRIGADE} or {@code PERSON}; default {@code BRIGADE}
 * @param journalVisibility {@code STAFF} or {@code EVERYONE}; default {@code STAFF}
 * @param retentionDays     at least 1; default 14, staff capped at 30
 * @param type              must be {@code QUEUE} when supplied
 */
public record CreateEventRequest(
        @NotBlank(message = "Название обязательно")
        @Size(max = 200, message = "Название не должно превышать 200 символов")
        String title,
        String description,
        Availability availability,
        Instant startsAt,
        Integer entryLimit,
        EntryUnit entryUnit,
        JournalVisibility journalVisibility,
        Integer retentionDays,
        EventType type) {
}
