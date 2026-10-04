package ru.nstu.system.contracts.events;

import java.util.UUID;

/**
 * Payload of {@link EventTypes#ACCOUNT_BLOCKED}.
 *
 * @param accountId blocked account identifier
 */
public record AccountBlockedPayload(UUID accountId) {
}
