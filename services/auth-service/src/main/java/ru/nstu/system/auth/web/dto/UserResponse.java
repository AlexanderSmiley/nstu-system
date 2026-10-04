package ru.nstu.system.auth.web.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Account view returned by {@code GET /api/users} and {@code PATCH /api/users/{id}}
 * (identity spec "Просмотр списка пользователей", task 5.8).
 *
 * <p>Deliberately carries no password material: the hash and any temporary password
 * are never exposed by read or update endpoints.</p>
 *
 * @param id                 account identifier
 * @param username           username as stored
 * @param displayName        display name
 * @param email              optional email, may be {@code null}
 * @param role               role name without the {@code ROLE_} prefix
 * @param blocked            whether the account is blocked
 * @param mustChangePassword whether a mandatory password change is pending
 * @param createdAt          creation instant
 * @param updatedAt          last modification instant
 */
public record UserResponse(
        UUID id,
        String username,
        String displayName,
        String email,
        String role,
        boolean blocked,
        boolean mustChangePassword,
        Instant createdAt,
        Instant updatedAt) {
}
