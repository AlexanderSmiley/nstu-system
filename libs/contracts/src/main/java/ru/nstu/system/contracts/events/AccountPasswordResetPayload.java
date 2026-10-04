package ru.nstu.system.contracts.events;

import java.util.UUID;

/**
 * Payload of {@link EventTypes#ACCOUNT_PASSWORD_RESET}.
 *
 * <p>Fired when an administrator resets a password or when a user completes a
 * mandatory password change; all refresh tokens of the account are revoked in
 * the same transaction (design.md D8).</p>
 *
 * @param accountId account identifier
 */
public record AccountPasswordResetPayload(UUID accountId) {
}
