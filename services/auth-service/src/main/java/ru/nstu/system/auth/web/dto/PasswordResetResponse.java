package ru.nstu.system.auth.web.dto;

import java.util.UUID;

/**
 * Response of {@code POST /api/users/{id}/reset-password} (identity spec "Сброс
 * пароля администратором", task 5.10).
 *
 * <p>The generated {@code temporaryPassword} appears in this response and only
 * here: only its BCrypt hash is stored and it is never returned again. The
 * administrator hands it to the user, who must change it on first login
 * ({@code mustChangePassword}).</p>
 *
 * @param id                account identifier
 * @param username          username as stored
 * @param temporaryPassword one-time generated password
 * @param mustChangePassword always {@code true} after a reset
 */
public record PasswordResetResponse(
        UUID id,
        String username,
        String temporaryPassword,
        boolean mustChangePassword) {
}
