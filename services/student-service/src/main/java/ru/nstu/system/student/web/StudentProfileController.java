package ru.nstu.system.student.web;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.nstu.system.security.ParsedToken;
import ru.nstu.system.security.RoleNames;
import ru.nstu.system.security.SecurityContextSupport;
import ru.nstu.system.student.domain.StudentProfile;
import ru.nstu.system.student.error.ApiException;
import ru.nstu.system.student.service.StudentProfileService;
import ru.nstu.system.student.web.dto.StudentProfileResponse;
import ru.nstu.system.student.web.dto.UpdateStudentProfileRequest;

/**
 * Self-service profile API, mounted under {@code /api/students} (design.md D4, D10;
 * task 6.1).
 *
 * <p>Both endpoints require a valid access token (401 without one, enforced by the
 * security filter chain). A guest session is rejected with 403: it has no profile.
 * An administrator passes authentication but has no profile either, so a lookup
 * yields 404.</p>
 */
@RestController
@RequestMapping("/api/students")
public class StudentProfileController {

    private final StudentProfileService profileService;

    public StudentProfileController(StudentProfileService profileService) {
        this.profileService = profileService;
    }

    /** Returns the authenticated caller's own profile. */
    @GetMapping("/me")
    public StudentProfileResponse me() {
        ParsedToken token = requireProfileOwner();
        return toResponse(requireProfile(accountId(token)));
    }

    /**
     * Updates the caller's own full name and/or contacts. When the full name
     * changes, a {@code profile.updated} event is appended to the outbox in the
     * same transaction (task 6.4).
     */
    @PatchMapping("/me")
    public StudentProfileResponse updateMe(@Valid @RequestBody UpdateStudentProfileRequest request) {
        ParsedToken token = requireProfileOwner();
        StudentProfile updated = profileService.updateOwnProfile(
                accountId(token), request.fullName(), request.contacts());
        return toResponse(updated);
    }

    /**
     * Maps a profile to its response, enriching it with the group name from the
     * directory. A group that is absent from the directory yields {@code null} and
     * never turns the profile request into an error (design.md D7).
     */
    private StudentProfileResponse toResponse(StudentProfile profile) {
        String groupName = profileService.findGroupName(profile.getGroupId()).orElse(null);
        return StudentProfileResponse.from(profile, groupName);
    }

    private StudentProfile requireProfile(UUID accountId) {
        return profileService.findProfile(accountId)
                .orElseThrow(() -> ApiException.notFound("profile_not_found", "Профиль не найден"));
    }

    private static ParsedToken requireProfileOwner() {
        ParsedToken token = SecurityContextSupport.currentToken()
                .orElseThrow(() -> ApiException.unauthorized("unauthorized", "Требуется аутентификация"));
        if (token.roles().contains(RoleNames.GUEST)) {
            throw ApiException.forbidden("guest_has_no_profile", "Гостевая сессия не имеет профиля");
        }
        return token;
    }

    private static UUID accountId(ParsedToken token) {
        try {
            return UUID.fromString(token.subject());
        } catch (IllegalArgumentException ex) {
            // Only a well-formed account id can own a profile; anything else
            // (e.g. a guest subject) is treated as unauthorised for this resource.
            throw ApiException.unauthorized("unauthorized", "Требуется аутентификация");
        }
    }
}
