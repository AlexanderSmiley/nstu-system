package ru.nstu.system.event.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import ru.nstu.system.event.domain.EntryUnit;
import ru.nstu.system.event.domain.EventStatus;

/**
 * Full queue state returned by {@code GET /api/events/{id}/queue} and by every
 * state-changing queue operation (spec "Порядок очереди", "Журнал сдач").
 *
 * <p>{@code journal} is {@code null} — and therefore absent from the JSON, thanks
 * to {@link JsonInclude.Include#NON_NULL} — unless the caller is staff/admin or
 * the event's {@code journalVisibility} is {@code EVERYONE}. This is also why the
 * ETag is computed over the caller-specific projection: two roles may receive
 * different representations of the same event.</p>
 *
 * @param eventId     event identifier
 * @param eventStatus {@code OPEN}/{@code CLOSED}/{@code ARCHIVED}
 * @param entryLimit  maximum number of active entries
 * @param entryUnit   {@code BRIGADE}/{@code PERSON} (UI label only in the MVP)
 * @param updatedAt   event last-modified instant
 * @param queue       active entries ordered by ascending position
 * @param journal     passed entries ordered by surrender time, or {@code null} when hidden
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QueueStateResponse(
        UUID eventId,
        EventStatus eventStatus,
        int entryLimit,
        EntryUnit entryUnit,
        Instant updatedAt,
        List<QueueEntryResponse> queue,
        List<QueueEntryResponse> journal) {
}
