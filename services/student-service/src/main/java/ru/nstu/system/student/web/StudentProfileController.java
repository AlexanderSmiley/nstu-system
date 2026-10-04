package ru.nstu.system.student.web;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.nstu.system.student.domain.StudentProfile;
import ru.nstu.system.student.service.StudentProfileService;
import ru.nstu.system.student.web.dto.StudentProfileResponse;
import ru.nstu.system.student.web.dto.UpdateStudentProfileRequest;

/**
 * Self-service profile API, mounted under {@code /api/students} (design.md D4, D10;
 * task 6.1).
 *
 * <p>Both endpoints require a valid access token (401 without one, enforced by the
 * security filter chain). A guest session is rejected with 403: it has no profile.
 * Every account owns a profile: one is created by {@code account.created}, and an
 * account that predates that rule (including an administrator) gets one lazily on
 * its first call here (change add-preferences-and-calendar-ui, design.md D1).</p>
 */
@RestController
@RequestMapping("/api/students")
public class StudentProfileController {

    private final StudentProfileService profileService;

    public StudentProfileController(StudentProfileService profileService) {
        this.profileService = profileService;
    }

    /** Returns the authenticated caller's own profile, creating it if missing. */
    @GetMapping("/me")
    public StudentProfileResponse me() {
        return toResponse(profileService.getOrCreateProfile(requireAccountId()));
    }

    /**
     * Updates the caller's own full name and/or contacts. When the full name
     * changes, a {@code profile.updated} event is appended to the outbox in the
     * same transaction (task 6.4). A missing profile is created lazily first.
     */
    @PatchMapping("/me")
    public StudentProfileResponse updateMe(@Valid @RequestBody UpdateStudentProfileRequest request) {
        StudentProfile updated = profileService.updateOwnProfile(
                requireAccountId(), request.fullName(), request.contacts());
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

    /**
     * Resolves the caller's account id. A guest is rejected with 403; the account
     * itself may or may not have a profile yet (see {@code getOrCreateProfile}).
     */
    private static UUID requireAccountId() {
        return AccountIdentity.requireAccountId("guest_has_no_profile", "Гостевая сессия не имеет профиля");
    }
}
