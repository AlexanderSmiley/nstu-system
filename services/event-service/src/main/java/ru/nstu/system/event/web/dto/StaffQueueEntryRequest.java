package ru.nstu.system.event.web.dto;

/**
 * Body of {@code POST /api/events/{id}/queue/staff} (task 9.3; spec "Записи,
 * созданные персоналом за участника").
 *
 * <p>The name is validated by the service exactly like a joining name
 * ({@code QueueNameNormalizer}, 1..120 chars); a missing/blank value yields
 * {@code 400 invalid_name} rather than a generic binding error, which is why the
 * field carries no bean-validation annotation.</p>
 *
 * @param name display name of the stub, required and non-blank
 */
public record StaffQueueEntryRequest(String name) {
}
