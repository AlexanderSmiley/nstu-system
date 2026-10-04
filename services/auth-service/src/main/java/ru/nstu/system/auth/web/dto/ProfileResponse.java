package ru.nstu.system.auth.web.dto;

import java.util.UUID;

/**
 * Account profile returned by login, refresh and password change
 * (identity spec "Успешный вход").
 *
 * @param id                 account identifier
 * @param username           username as stored
 * @param displayName        display name (equals the username for the administrator)
 * @param role               role name without the {@code ROLE_} prefix
 * @param mustChangePassword whether a mandatory password change is still pending
 * @param email              account email, or {@code null} when unset
 */
public record ProfileResponse(
        UUID id,
        String username,
        String displayName,
        String role,
        boolean mustChangePassword,
        String email) {
}
