package ru.nstu.system.contracts.events;

import java.util.UUID;

/**
 * Payload of {@link EventTypes#ACCOUNT_CREATED}.
 *
 * <p>Emitted by {@code auth-service} when an administrator (or the bootstrap)
 * creates an account. Contains no personal data beyond the display name.</p>
 *
 * @param accountId   new account identifier
 * @param username    login name
 * @param role        one of the {@code ru.nstu.system.security.RoleNames} constants
 * @param displayName human-readable name kept in the auth schema
 */
public record AccountCreatedPayload(
        UUID accountId,
        String username,
        String role,
        String displayName) {
}
