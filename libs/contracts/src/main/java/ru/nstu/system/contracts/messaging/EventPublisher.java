package ru.nstu.system.contracts.messaging;

import ru.nstu.system.contracts.events.DomainEvent;

/**
 * Publishes a {@link DomainEvent} to the shared event exchange.
 *
 * <p>Implemented by {@link RabbitEventPublisher}; the interface keeps the
 * outbox publisher and services independent of the transport and makes them
 * easy to fake in tests.</p>
 */
public interface EventPublisher {

    /**
     * Publishes an event using its {@code eventType} as the routing key.
     *
     * @param event event to publish
     * @return the {@code eventId} of the published event
     */
    java.util.UUID publish(DomainEvent<?> event);
}
