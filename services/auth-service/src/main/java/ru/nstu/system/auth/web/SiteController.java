package ru.nstu.system.auth.web;

import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.nstu.system.auth.service.SiteSettingsService;
import ru.nstu.system.auth.web.dto.SiteNameRequest;
import ru.nstu.system.auth.web.dto.SiteNameResponse;
import ru.nstu.system.security.ParsedToken;
import ru.nstu.system.security.SecurityContextSupport;

/**
 * Site settings API (design.md D27, task 5.12).
 *
 * <p>{@code GET /api/site} is public so the login screen can render the site name
 * before authentication; it exposes only the name. {@code PUT /api/admin/site} is
 * ADMIN-only, enforced in
 * {@link ru.nstu.system.auth.config.AuthSecurityConfig}, and returns the stored
 * value.</p>
 */
@RestController
public class SiteController {

    private final SiteSettingsService siteSettingsService;

    public SiteController(SiteSettingsService siteSettingsService) {
        this.siteSettingsService = siteSettingsService;
    }

    /** Public: the site name for the login screen and the header (task 5.12). */
    @GetMapping("/api/site")
    public SiteNameResponse getSite() {
        return new SiteNameResponse(siteSettingsService.getName());
    }

    /** ADMIN-only: changes the site name and returns the stored value (task 5.12). */
    @PutMapping("/api/admin/site")
    public SiteNameResponse updateSite(@RequestBody SiteNameRequest request) {
        return new SiteNameResponse(
                siteSettingsService.updateName(currentAccountId(), request.name()));
    }

    private static UUID currentAccountId() {
        ParsedToken token = SecurityContextSupport.currentToken()
                .orElseThrow(() -> ApiException.unauthorized("unauthorized", "Требуется аутентификация"));
        try {
            return UUID.fromString(token.subject());
        } catch (IllegalArgumentException ex) {
            throw ApiException.unauthorized("unauthorized", "Сессия недействительна");
        }
    }
}
