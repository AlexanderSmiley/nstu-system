package ru.nstu.system.student.web;

import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.nstu.system.student.service.ProfilePreferencesService;
import ru.nstu.system.student.web.dto.ProfilePreferencesResponse;
import ru.nstu.system.student.web.dto.UpdatePreferencesRequest;

/**
 * Per-account UI preferences API, mounted under
 * {@code /api/students/me/preferences} (change add-preferences-and-calendar-ui,
 * design.md D3).
 *
 * <p>Both endpoints run inside the service's existing security chain: a missing
 * or invalid token is answered with 401 before reaching the controller, and a
 * mandatory-password-change token is blocked by the existing
 * {@code PasswordChangeRequiredFilter}. A guest session is authenticated but
 * owns no account, so it receives 403 here.</p>
 */
@RestController
@RequestMapping("/api/students/me/preferences")
public class ProfilePreferencesController {

    private final ProfilePreferencesService preferencesService;

    public ProfilePreferencesController(ProfilePreferencesService preferencesService) {
        this.preferencesService = preferencesService;
    }

    /** Returns the caller's preferences, materialising defaults on first access. */
    @GetMapping
    public ProfilePreferencesResponse get() {
        return preferencesService.getPreferences(accountId());
    }

    /** Partially updates the caller's modules and/or calendar colours. */
    @PatchMapping
    public ProfilePreferencesResponse update(@RequestBody(required = false) UpdatePreferencesRequest request) {
        return preferencesService.updatePreferences(accountId(), request);
    }

    private static UUID accountId() {
        return AccountIdentity.requireAccountId(
                "guest_has_no_preferences", "Гостевая сессия не имеет персональных настроек");
    }
}
