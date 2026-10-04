package ru.nstu.system.contracts.events;

import java.util.Set;

/**
 * Canonical names of the domain events exchanged over RabbitMQ (design.md D2, D14).
 *
 * <p>The value of a constant is used verbatim as the AMQP routing key on the
 * {@code nstu.events} topic exchange, which makes a subscription expressible as
 * {@code account.*}, {@code #} or an exact name.</p>
 */
public final class EventTypes {

    /** Current payload contract version carried by {@link DomainEvent#version()}. */
    public static final int CURRENT_VERSION = 1;

    public static final String ACCOUNT_CREATED = "account.created";

    public static final String ACCOUNT_UPDATED = "account.updated";

    public static final String ACCOUNT_BLOCKED = "account.blocked";

    public static final String ACCOUNT_UNBLOCKED = "account.unblocked";

    public static final String ACCOUNT_PASSWORD_RESET = "account.password_reset";

    public static final String PROFILE_UPDATED = "profile.updated";

    public static final String EVENT_CLOSED = "event.closed";

    public static final String ENTRY_PASSED = "entry.passed";

    public static final String QUEUE_ADVANCED = "queue.advanced";

    public static final String EVENT_ARCHIVED = "event.archived";

    /** Every event type known to the system. */
    public static final Set<String> ALL = Set.of(
            ACCOUNT_CREATED,
            ACCOUNT_UPDATED,
            ACCOUNT_BLOCKED,
            ACCOUNT_UNBLOCKED,
            ACCOUNT_PASSWORD_RESET,
            PROFILE_UPDATED,
            EVENT_CLOSED,
            ENTRY_PASSED,
            QUEUE_ADVANCED,
            EVENT_ARCHIVED);

    private EventTypes() {
    }
}
