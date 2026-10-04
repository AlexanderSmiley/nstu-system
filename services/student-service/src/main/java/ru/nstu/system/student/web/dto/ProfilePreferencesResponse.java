package ru.nstu.system.student.web.dto;

import java.util.Map;

/**
 * Preferences returned by {@code GET/PATCH /api/students/me/preferences}
 * (change add-preferences-and-calendar-ui, design.md D3/D4).
 *
 * <p>Every allowed module and audience is always present: the service fills in
 * defaults for keys a stored row might lack, so the client never has to guess.</p>
 *
 * @param modules        enabled state of {@code events}/{@code calendar}/{@code notes}
 * @param calendarColors fill colour of {@code ME}/{@code GROUP}/{@code STAFF}
 */
public record ProfilePreferencesResponse(
        Map<String, Boolean> modules,
        Map<String, String> calendarColors) {
}
