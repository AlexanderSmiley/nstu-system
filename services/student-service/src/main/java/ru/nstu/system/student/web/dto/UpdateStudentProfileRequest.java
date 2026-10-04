package ru.nstu.system.student.web.dto;

import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * Body of {@code PATCH /api/students/me} (task 6.1).
 *
 * <p>Partial update: a {@code null} field is left unchanged. {@code groupId} is
 * deliberately not accepted — the owner cannot move themselves to another group.
 * An empty {@code fullName} and an over-long value are rejected by the service.</p>
 *
 * @param fullName new full name, or {@code null} to keep the current one
 * @param contacts new contact map, or {@code null} to keep the current one
 */
public record UpdateStudentProfileRequest(
        @Size(max = 255, message = "ФИО не должно превышать 255 символов") String fullName,
        Map<String, Object> contacts) {
}
