package ru.nstu.system.event.web.dto;

import jakarta.validation.constraints.Size;
import java.time.Instant;
import ru.nstu.system.event.domain.Availability;
import ru.nstu.system.event.domain.EntryUnit;
import ru.nstu.system.event.domain.JournalVisibility;

/**
 * Body of {@code PATCH /api/events/{id}} (task 7.5).
 *
 * <p>A {@code null} field means "leave unchanged"; this is why the request uses
 * wrappers instead of primitives. The full edit matrix — and the administrator-only
 * {@code slug}/{@code retentionDays} fields — is enforced in
 * {@link ru.nstu.system.event.service.EventService}, because it depends on the
 * caller role and on the current event state.</p>
 */
public record UpdateEventRequest(
        @Size(max = 200, message = "Название не должно превышать 200 символов")
        String title,
        String description,
        Availability availability,
        Instant startsAt,
        Integer entryLimit,
        EntryUnit entryUnit,
        JournalVisibility journalVisibility,
        Integer retentionDays,
        String slug) {
}
