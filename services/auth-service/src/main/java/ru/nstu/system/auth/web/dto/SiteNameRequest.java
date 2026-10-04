package ru.nstu.system.auth.web.dto;

/**
 * Body of {@code PUT /api/admin/site} (identity spec "Название сайта", task 5.12).
 *
 * <p>Only the site name is configurable in the MVP. The value is validated in the
 * service after trimming (non-empty, at most
 * {@link ru.nstu.system.auth.service.SiteSettingsService#MAX_NAME_LENGTH}
 * characters); carrying it as a plain string means an invalid value produces a
 * clear {@code 400} instead of a deserialisation failure.</p>
 *
 * @param name requested site name
 */
public record SiteNameRequest(String name) {
}
