package ru.nstu.system.student.web;

import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.nstu.system.student.domain.StudentProfile;
import ru.nstu.system.student.error.ApiException;
import ru.nstu.system.student.service.StudentProfileService;
import ru.nstu.system.student.web.dto.InternalStudentResponse;

/**
 * Internal, service-to-service API (design.md D12; task 6.3).
 *
 * <p>{@code event-service} calls this endpoint to take a snapshot of a student's
 * name when they join a queue. The namespace is not routed by the gateway and is
 * protected by the {@code X-Internal-Token} header (missing/wrong token -> 403),
 * enforced by {@link ru.nstu.system.student.config.InternalTokenFilter}. No JWT is
 * required.</p>
 */
@RestController
@RequestMapping("/internal/students")
public class InternalStudentController {

    private final StudentProfileService profileService;

    public InternalStudentController(StudentProfileService profileService) {
        this.profileService = profileService;
    }

    /** @return name and group for the given account, or 404 when it has no profile */
    @GetMapping("/{accountId}")
    public InternalStudentResponse getProfile(@PathVariable UUID accountId) {
        StudentProfile profile = profileService.findProfile(accountId)
                .orElseThrow(() -> ApiException.notFound("profile_not_found", "Профиль не найден"));
        return new InternalStudentResponse(profile.getId(), profile.getFullName(), profile.getGroupId());
    }
}
