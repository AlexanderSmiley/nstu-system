package ru.nstu.system.notification.messaging;

import ru.nstu.system.contracts.events.DomainEvent;

/**
 * Seam between the RabbitMQ listener and the actual reaction to a domain event.
 *
 * <p>In the MVP the only implementation is
 * {@link LoggingNotificationEventHandler} (design.md D22): it logs the event and
 * does nothing else. Future notification logic (telegram delivery, alerts) plugs
 * in here without touching the transport wiring.</p>
 */
@FunctionalInterface
public interface NotificationEventHandler {

    /**
     * Reacts to one domain event. The listener guarantees this is called at most
     * once per already-processed {@code eventId} and that a thrown exception
     * dead-letters the message.
     *
     * @param event received event
     */
    void handle(DomainEvent<?> event);
}
