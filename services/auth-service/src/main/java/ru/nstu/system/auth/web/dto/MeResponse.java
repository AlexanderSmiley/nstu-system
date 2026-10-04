package ru.nstu.system.auth.web.dto;

import java.util.Set;

/**
 * Body of {@code GET /api/auth/me} (task 5.4).
 *
 * @param subject            account id, or {@code guest:<uuid>} for a guest session
 * @param roles              role names without the {@code ROLE_} prefix
 * @param displayName        display name, or {@code null} for guests (their name is
 *                           captured when they join a queue, design.md D11)
 * @param mustChangePassword whether a mandatory password change is pending
 * @param guest              {@code true} for an anonymous guest session
 * @param username           account username, or {@code null} for guests
 * @param email              account email, or {@code null} for guests and when unset
 * @param role               single primary role ({@code ADMIN}/{@code STAFF}/
 *                           {@code STUDENT}, or {@code GUEST} for a guest session)
 */
public record MeResponse(
        String subject,
        Set<String> roles,
        String displayName,
        boolean mustChangePassword,
        boolean guest,
        String username,
        String email,
        String role) {
}
