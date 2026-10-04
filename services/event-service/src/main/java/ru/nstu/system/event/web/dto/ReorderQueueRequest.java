package ru.nstu.system.event.web.dto;

/**
 * Body of {@code PATCH /api/events/{id}/queue/{entryId}/position}
 * (spec "Перестановка записей").
 *
 * <p>The position is 1-based among the event's <em>active</em> entries; a value
 * outside {@code 1..K} is a 400. A primitive {@code int} maps a missing field to
 * {@code 0}, which is likewise rejected.</p>
 *
 * @param position target position of the moved active entry
 */
public record ReorderQueueRequest(int position) {
}
