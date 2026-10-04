package ru.nstu.system.student.web.dto;

import java.util.Map;

/**
 * Body of {@code PATCH /api/students/me/preferences}
 * (change add-preferences-and-calendar-ui, design.md D3).
 *
 * <p>Partial update: a {@code null} field is left unchanged, and inside a map
 * only the supplied keys are updated. Values are typed as {@link Object} so the
 * service can reject a wrong JSON type explicitly (unknown module or non-boolean
 * value, colour that is not {@code #RRGGBB}) with a stable error code instead of
 * relying on Jackson coercion.</p>
 *
 * @param modules        subset of {@code events}/{@code calendar}/{@code notes} to change
 * @param calendarColors subset of {@code ME}/{@code GROUP}/{@code STAFF} colours to change
 */
public record UpdatePreferencesRequest(
        Map<String, Object> modules,
        Map<String, Object> calendarColors) {
}
