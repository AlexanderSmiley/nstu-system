package ru.nstu.system.student.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Plain-JDBC store for {@code student.profile_preferences}
 * (change add-preferences-and-calendar-ui, design.md D2).
 *
 * <p>The two payload columns are {@code jsonb}; the shared Jackson
 * {@link ObjectMapper} is used to (de)serialise them so the mapping stays
 * explicit and independent of Hibernate's JSON handling. The repository performs
 * no validation or defaulting — that is the service's job.</p>
 */
@Repository
public class ProfilePreferencesRepository {

    private static final String SELECT_BY_PROFILE = "select modules::text as modules, "
            + "calendar_colors::text as calendar_colors, updated_at "
            + "from student.profile_preferences where profile_id = ?";

    private static final String INSERT_DEFAULT = "insert into student.profile_preferences (profile_id) "
            + "values (?) on conflict (profile_id) do nothing";

    private static final String UPDATE = "update student.profile_preferences "
            + "set modules = ?::jsonb, calendar_colors = ?::jsonb, updated_at = ? where profile_id = ?";

    private static final TypeReference<Map<String, Boolean>> MODULES_TYPE =
            new TypeReference<>() {
            };

    private static final TypeReference<Map<String, String>> COLORS_TYPE =
            new TypeReference<>() {
            };

    private final JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper;

    public ProfilePreferencesRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    /** @return the stored preferences, or empty when the row does not exist yet */
    public Optional<ProfilePreferences> findByProfileId(UUID profileId) {
        Objects.requireNonNull(profileId, "profileId");
        return jdbcTemplate.query(SELECT_BY_PROFILE, (resultSet, rowNumber) -> new ProfilePreferences(
                        profileId,
                        read(resultSet.getString("modules"), MODULES_TYPE),
                        read(resultSet.getString("calendar_colors"), COLORS_TYPE),
                        resultSet.getObject("updated_at", OffsetDateTime.class).toInstant()),
                profileId).stream().findFirst();
    }

    /**
     * Ensures a row exists, letting the database defaults fill it. Idempotent and
     * race-safe: a concurrent insert simply does nothing.
     */
    public void insertDefaultIfAbsent(UUID profileId) {
        Objects.requireNonNull(profileId, "profileId");
        jdbcTemplate.update(INSERT_DEFAULT, profileId);
    }

    /** Replaces both payloads and {@code updated_at} for an existing row. */
    public void update(UUID profileId,
                       Map<String, Boolean> modules,
                       Map<String, String> calendarColors,
                       Instant updatedAt) {
        Objects.requireNonNull(profileId, "profileId");
        Objects.requireNonNull(modules, "modules");
        Objects.requireNonNull(calendarColors, "calendarColors");
        Objects.requireNonNull(updatedAt, "updatedAt");
        jdbcTemplate.update(UPDATE, write(modules), write(calendarColors),
                OffsetDateTime.ofInstant(updatedAt, java.time.ZoneOffset.UTC), profileId);
    }

    private <T> T read(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            // A jsonb column can only contain valid JSON, so this signals a real
            // data corruption rather than a client error.
            throw new IllegalStateException("Cannot read profile preferences JSON", exception);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot write profile preferences JSON", exception);
        }
    }
}
