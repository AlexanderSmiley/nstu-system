package ru.nstu.system.contracts.events;

import java.util.UUID;

/**
 * Payload of {@link EventTypes#ACCOUNT_UPDATED}.
 *
 * <p>Mirrors the mutable account attributes so consumers can refresh a local
 * projection without a synchronous call back to {@code auth-service}.</p>
 *
 * @param accountId   account identifier
 * @param username    login name
 * @param role        one of the {@code ru.nstu.system.security.RoleNames} constants
 * @param displayName human-readable name kept in the auth schema
 */
public record AccountUpdatedPayload(
        UUID accountId,
        String username,
        String role,
        String displayName) {
}
