package ru.nstu.system.contracts.messaging;

/**
 * Names of the shared RabbitMQ topology (design.md D13).
 *
 * <p>Topology: a durable {@code topic} exchange {@link #EXCHANGE}; every
 * consumer service owns a durable queue named
 * {@link #CONSUMER_QUEUE_PREFIX}{@code <service>} bound with {@code #}; queues
 * declare {@code x-dead-letter-exchange = }{@link #DLX}; the shared
 * {@link #DLQ} is bound to {@link #DLX} with {@code #}.</p>
 *
 * <p><strong>Routing key convention:</strong> the routing key equals the event
 * type ({@link ru.nstu.system.contracts.events.EventTypes}), e.g.
 * {@code account.created}. Subscriptions therefore use patterns such as
 * {@code account.*} or {@code #}.</p>
 */
public final class RabbitNames {

    /** Durable topic exchange carrying all domain events. */
    public static final String EXCHANGE = "nstu.events";

    /** Dead-letter exchange; consumers set it as {@code x-dead-letter-exchange}. */
    public static final String DLX = "nstu.events.dlx";

    /** Shared dead-letter queue for poisoned or rejected messages. */
    public static final String DLQ = "nstu.events.dlq";

    /** Binding pattern used to attach the DLQ to the DLX (and queues to the exchange). */
    public static final String MATCH_ALL_ROUTING_KEY = "#";

    /** Prefix of every consumer queue: {@code nstu.events.<service>}. */
    public static final String CONSUMER_QUEUE_PREFIX = "nstu.events.";

    private RabbitNames() {
    }
}
