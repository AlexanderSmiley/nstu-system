package ru.nstu.system.auth.web.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response of {@code POST /api/users} (task 5.8).
 *
 * <p>The generated {@code temporaryPassword} is present in this response and only
 * here: it is never persisted in clear text and never returned again (identity spec
 * "Повторный просмотр временного пароля"). The administrator is expected to hand it
 * to the user, who must change it on first login ({@code mustChangePassword}).</p>
 *
 * @param id                account identifier
 * @param username          username as stored
 * @param displayName       display name
 * @param email             optional email, may be {@code null}
 * @param role              role name without the {@code ROLE_} prefix
 * @param mustChangePassword always {@code true} for a freshly created account
 * @param temporaryPassword one-time generated password
 * @param createdAt         creation instant
 */
public record CreatedUserResponse(
        UUID id,
        String username,
        String displayName,
        String email,
        String role,
        boolean mustChangePassword,
        String temporaryPassword,
        Instant createdAt) {
}
