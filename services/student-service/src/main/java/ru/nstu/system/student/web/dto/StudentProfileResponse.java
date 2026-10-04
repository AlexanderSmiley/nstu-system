package ru.nstu.system.student.web.dto;

import java.util.Map;
import java.util.UUID;
import ru.nstu.system.student.domain.StudentProfile;

/**
 * Profile returned by {@code GET/PATCH /api/students/me}.
 *
 * <p>{@code accountId} equals the account id, {@code groupId} is read-only.
 * {@code groupName} is the display name of {@code groupId} resolved from the
 * group directory; it is {@code null} when the directory has no entry for the
 * group, in which case the profile is still returned successfully. The email is
 * intentionally absent: it is owned by {@code auth-service}.</p>
 */
public record StudentProfileResponse(
        UUID accountId,
        String fullName,
        UUID groupId,
        String groupName,
        Map<String, Object> contacts) {

    public static StudentProfileResponse from(StudentProfile profile, String groupName) {
        return new StudentProfileResponse(
                profile.getId(),
                profile.getFullName(),
                profile.getGroupId(),
                groupName,
                profile.getContacts());
    }
}
