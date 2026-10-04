package ru.nstu.system.contracts.events;

import java.util.UUID;

/**
 * Payload of {@link EventTypes#PROFILE_UPDATED}.
 *
 * <p>Emitted by {@code student-service}. Personal data stays inside the student
 * schema; only the full name is propagated because event cards need it
 * (design.md D2).</p>
 *
 * @param accountId owning account identifier
 * @param fullName  updated full name
 */
public record ProfileUpdatedPayload(UUID accountId, String fullName) {
}
