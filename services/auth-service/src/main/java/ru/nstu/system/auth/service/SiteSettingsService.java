package ru.nstu.system.auth.service;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.nstu.system.auth.web.ApiException;

/**
 * Site settings backed by {@code auth.site_setting} (design.md D27, task 5.12).
 *
 * <p>The table is a key/value store; in the MVP the only entry is
 * {@code site.name}. The name is public (the login screen renders it before
 * authentication) while writing it is ADMIN-only, enforced in
 * {@link ru.nstu.system.auth.config.AuthSecurityConfig}. Reads and writes go
 * through {@link JdbcTemplate} because {@code value} is {@code jsonb}: a plain
 * SQL round-trip is simpler and more explicit than a JSON JPA mapping.</p>
 *
 * <p>The {@code GET} deliberately returns a single scalar, not the whole row, so
 * future settings cannot accidentally leak through the public endpoint.</p>
 */
@Service
public class SiteSettingsService {

    /** Primary key of the site name entry. */
    public static final String SITE_NAME_KEY = "site.name";

    /** Maximum accepted length of the site name. */
    public static final int MAX_NAME_LENGTH = 120;

    /**
     * Fallback used only if the seeded row is missing; the migration
     * {@code V2__site_settings.sql} always inserts it, so reads must keep working
     * even against a manually damaged database.
     */
    static final String DEFAULT_SITE_NAME = "NSTU System";

    private final JdbcTemplate jdbcTemplate;

    public SiteSettingsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * @return the current site name, or {@link #DEFAULT_SITE_NAME} if absent
     */
    @Transactional(readOnly = true)
    public String getName() {
        List<String> values = jdbcTemplate.query(
                "select value #>> '{}' from auth.site_setting where key = ?",
                (resultSet, rowNumber) -> resultSet.getString(1),
                SITE_NAME_KEY);
        if (values.isEmpty() || values.get(0) == null) {
            return DEFAULT_SITE_NAME;
        }
        return values.get(0);
    }

    /**
     * Validates and stores a new site name, recording who changed it.
     *
     * <p>The value is trimmed before validation, so a whitespace-only name is
     * rejected and the length limit applies to the meaningful value. The row is
     * upserted so the endpoint also recovers if the seed row was removed.</p>
     *
     * @param actorId administrator performing the change (recorded in {@code updated_by})
     * @param rawName requested name, may be {@code null}
     * @return the stored, trimmed name
     * @throws ApiException 400 when the trimmed name is empty or longer than
     *                      {@link #MAX_NAME_LENGTH}
     */
    @Transactional
    public String updateName(UUID actorId, String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty()) {
            throw ApiException.badRequest("invalid_site_name", "Название сайта не может быть пустым");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw ApiException.badRequest("invalid_site_name",
                    "Название сайта не должно превышать " + MAX_NAME_LENGTH + " символов");
        }

        jdbcTemplate.update(
                "insert into auth.site_setting (key, value, updated_at, updated_by)"
                        + " values (?, to_jsonb(cast(? as text)), now(), ?)"
                        + " on conflict (key) do update"
                        + " set value = excluded.value,"
                        + "     updated_at = excluded.updated_at,"
                        + "     updated_by = excluded.updated_by",
                SITE_NAME_KEY,
                name,
                actorId);
        return name;
    }
}
