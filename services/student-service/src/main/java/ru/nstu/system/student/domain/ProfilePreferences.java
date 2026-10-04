package ru.nstu.system.student.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Per-account UI preferences stored in {@code student.profile_preferences}
 * (change add-preferences-and-calendar-ui, design.md D2).
 *
 * <p>The primary key equals {@code student.student_profile.id}, so preferences
 * share the profile lifecycle. The service layer is responsible for filling in
 * missing keys with defaults; the record itself is a plain carrier.</p>
 *
 * @param profileId      owning profile id
 * @param modules        enabled home-page modules ({@code events}/{@code calendar}/{@code notes})
 * @param calendarColors calendar fill colours keyed by audience ({@code ME}/{@code GROUP}/{@code STAFF})
 * @param updatedAt      last modification instant
 */
public record ProfilePreferences(
        UUID profileId,
        Map<String, Boolean> modules,
        Map<String, String> calendarColors,
        Instant updatedAt) {

    public ProfilePreferences {
        Objects.requireNonNull(profileId, "profileId");
        modules = modules == null ? Map.of() : Map.copyOf(modules);
        calendarColors = calendarColors == null ? Map.of() : Map.copyOf(calendarColors);
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
