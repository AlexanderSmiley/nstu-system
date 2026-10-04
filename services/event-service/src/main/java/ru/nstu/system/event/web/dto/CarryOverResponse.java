package ru.nstu.system.event.web.dto;

import java.util.List;
import java.util.UUID;

/**
 * Result of {@code POST /api/events/{id}/carry-over} (task 9.2; design.md D18).
 *
 * <p>Carrying the queue tail is best-effort by design: an entry whose name is
 * already taken in the target is skipped ({@code name_taken}) and, once the
 * target's entry limit is reached, the remaining entries are skipped
 * ({@code queue_full}). The response reports both sets so the staff UI can
 * explain exactly what happened.</p>
 *
 * @param added   entries created in the target event
 * @param skipped entries that could not be carried, with a machine-readable reason
 */
public record CarryOverResponse(List<Added> added, List<Skipped> skipped) {

    /** A source entry materialised in the target event. */
    public record Added(UUID sourceEntryId, UUID targetEntryId, String name) {
    }

    /** A source entry that was not carried; {@code reason} is {@code name_taken} or {@code queue_full}. */
    public record Skipped(UUID sourceEntryId, String name, String reason) {
    }
}
