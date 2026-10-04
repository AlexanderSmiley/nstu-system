package ru.nstu.system.student.web.dto;

import java.util.UUID;

/**
 * Body of {@code GET /internal/students/{accountId}}.
 *
 * <p>This is the name snapshot consumed by {@code event-service} when a student
 * joins a queue (design.md D12, task 6.3). Only the fields needed for the snapshot
 * are exposed: the account id, the full name and the group id. Contacts are
 * deliberately omitted — they are not needed and are more sensitive.</p>
 */
public record InternalStudentResponse(UUID accountId, String fullName, UUID groupId) {
}
