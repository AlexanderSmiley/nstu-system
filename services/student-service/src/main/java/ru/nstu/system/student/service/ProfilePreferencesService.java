package ru.nstu.system.student.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.nstu.system.student.domain.ProfilePreferences;
import ru.nstu.system.student.domain.ProfilePreferencesRepository;
import ru.nstu.system.student.error.ApiException;
import ru.nstu.system.student.web.dto.ProfilePreferencesResponse;
import ru.nstu.system.student.web.dto.UpdatePreferencesRequest;

/**
 * Per-account UI preferences: lazy creation with defaults, partial update and
 * validation (change add-preferences-and-calendar-ui, design.md D2-D4).
 *
 * <p>Preferences hang off the profile, so a missing profile is created first
 * (for accounts that predate the "profile for every account" rule). The
 * preferences row itself is created lazily on the first read with the database
 * defaults; a race between two first reads is absorbed by
 * {@code insert ... on conflict do nothing} plus a re-read.</p>
 */
@Service
public class ProfilePreferencesService {

    /** Home-page module identifiers accepted from clients. */
    private static final Set<String> ALLOWED_MODULES = Set.of("events", "calendar", "notes");

    /** Calendar audience identifiers accepted from clients. */
    private static final Set<String> ALLOWED_AUDIENCES = Set.of("ME", "GROUP", "STAFF");

    /** Default module state: everything enabled (design.md D4). */
    private static final Map<String, Boolean> DEFAULT_MODULES = orderedModules();

    /** Default calendar colours (design.md D4). */
    private static final Map<String, String> DEFAULT_COLORS = orderedColors();

    /** A colour must be exactly {@code #RRGGBB}. */
    private static final Pattern COLOR_PATTERN = Pattern.compile("^#[0-9a-fA-F]{6}$");

    private final StudentProfileService profileService;

    private final ProfilePreferencesRepository preferencesRepository;

    public ProfilePreferencesService(StudentProfileService profileService,
                                     ProfilePreferencesRepository preferencesRepository) {
        this.profileService = Objects.requireNonNull(profileService, "profileService");
        this.preferencesRepository = Objects.requireNonNull(preferencesRepository, "preferencesRepository");
    }

    /**
     * Returns the caller's preferences, creating the profile and the preferences
     * row on first access with the defaults.
     */
    @Transactional
    public ProfilePreferencesResponse getPreferences(UUID accountId) {
        Objects.requireNonNull(accountId, "accountId");
        return toResponse(loadOrCreate(accountId));
    }

    /**
     * Applies a partial update: only the supplied module keys and audiences
     * change, the rest is preserved.
     *
     * @throws ApiException 400 {@code invalid_module} for an unknown module or a
     *                      non-boolean value, {@code invalid_audience} for an
     *                      unknown audience, {@code invalid_color} for a value
     *                      that is not {@code #RRGGBB}
     */
    @Transactional
    public ProfilePreferencesResponse updatePreferences(UUID accountId, UpdatePreferencesRequest request) {
        Objects.requireNonNull(accountId, "accountId");
        ProfilePreferences current = loadOrCreate(accountId);

        Map<String, Boolean> modules = mergeModules(current.modules());
        Map<String, String> colors = mergeColors(current.calendarColors());

        if (request != null && request.modules() != null) {
            request.modules().forEach((module, value) -> {
                requireKnownModule(module);
                if (!(value instanceof Boolean enabled)) {
                    throw invalidModule(module);
                }
                modules.put(module, enabled);
            });
        }
        if (request != null && request.calendarColors() != null) {
            request.calendarColors().forEach((audience, value) -> {
                requireKnownAudience(audience);
                if (!(value instanceof String color) || !COLOR_PATTERN.matcher(color).matches()) {
                    throw invalidColor();
                }
                colors.put(audience, color);
            });
        }

        preferencesRepository.update(accountId, modules, colors, Instant.now());
        return new ProfilePreferencesResponse(modules, colors);
    }

    /**
     * Loads the stored row, creating the profile and the row when either is
     * missing. The re-read makes concurrent first accesses converge on one row.
     */
    private ProfilePreferences loadOrCreate(UUID accountId) {
        // The profile is the foreign-key parent; make sure it exists first.
        profileService.getOrCreateProfile(accountId);
        return preferencesRepository.findByProfileId(accountId)
                .orElseGet(() -> {
                    preferencesRepository.insertDefaultIfAbsent(accountId);
                    return preferencesRepository.findByProfileId(accountId)
                            .orElseThrow(() -> new IllegalStateException(
                                    "student.profile_preferences row for " + accountId
                                            + " is missing after an upsert"));
                });
    }

    /** Fills in missing/unknown stored modules with defaults, preserving order. */
    private static Map<String, Boolean> mergeModules(Map<String, Boolean> stored) {
        Map<String, Boolean> merged = new LinkedHashMap<>();
        for (Map.Entry<String, Boolean> defaultEntry : DEFAULT_MODULES.entrySet()) {
            Boolean value = stored.get(defaultEntry.getKey());
            merged.put(defaultEntry.getKey(), value == null ? defaultEntry.getValue() : value);
        }
        return merged;
    }

    /** Fills in missing/unknown stored colours with defaults, preserving order. */
    private static Map<String, String> mergeColors(Map<String, String> stored) {
        Map<String, String> merged = new LinkedHashMap<>();
        for (Map.Entry<String, String> defaultEntry : DEFAULT_COLORS.entrySet()) {
            String value = stored.get(defaultEntry.getKey());
            merged.put(defaultEntry.getKey(), value == null ? defaultEntry.getValue() : value);
        }
        return merged;
    }

    private static ProfilePreferencesResponse toResponse(ProfilePreferences preferences) {
        return new ProfilePreferencesResponse(
                mergeModules(preferences.modules()),
                mergeColors(preferences.calendarColors()));
    }

    private static void requireKnownModule(String module) {
        if (module == null || !ALLOWED_MODULES.contains(module)) {
            throw invalidModule(module);
        }
    }

    private static void requireKnownAudience(String audience) {
        if (audience == null || !ALLOWED_AUDIENCES.contains(audience)) {
            throw ApiException.badRequest("invalid_audience",
                    "Неизвестный адресат цвета: " + audience);
        }
    }

    private static ApiException invalidModule(String module) {
        return ApiException.badRequest("invalid_module", "Неизвестный модуль: " + module);
    }

    private static ApiException invalidColor() {
        return ApiException.badRequest("invalid_color", "Цвет должен быть в формате #RRGGBB");
    }

    private static Map<String, Boolean> orderedModules() {
        Map<String, Boolean> modules = new LinkedHashMap<>();
        modules.put("events", true);
        modules.put("calendar", true);
        modules.put("notes", true);
        return java.util.Collections.unmodifiableMap(modules);
    }

    private static Map<String, String> orderedColors() {
        Map<String, String> colors = new LinkedHashMap<>();
        colors.put("ME", "#ffffff");
        colors.put("GROUP", "#cfe3ff");
        colors.put("STAFF", "#d9dde3");
        return java.util.Collections.unmodifiableMap(colors);
    }
}
