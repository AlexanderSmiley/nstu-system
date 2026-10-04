package ru.nstu.system.auth.web.dto;

/**
 * Response of {@code GET /api/site} and {@code PUT /api/admin/site}
 * (identity spec "Название сайта", task 5.12).
 *
 * <p>The public {@code GET} exposes exactly this one field: the login screen and
 * the header only need the site name, so no other setting may leak through it
 * (design.md D27).</p>
 *
 * @param name current site name
 */
public record SiteNameResponse(String name) {
}
