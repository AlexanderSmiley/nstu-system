package ru.nstu.system.contracts.events;

import java.util.UUID;

/**
 * Payload of {@link EventTypes#ACCOUNT_UNBLOCKED}.
 *
 * @param accountId unblocked account identifier
 */
public record AccountUnblockedPayload(UUID accountId) {
}
