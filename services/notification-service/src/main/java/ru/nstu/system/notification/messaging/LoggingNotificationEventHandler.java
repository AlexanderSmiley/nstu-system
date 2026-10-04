package ru.nstu.system.notification.messaging;

import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.nstu.system.contracts.events.DomainEvent;

/**
 * MVP implementation of {@link NotificationEventHandler}: logs every received
 * event at INFO (task 10.1) and performs no delivery (design.md D22).
 *
 * <p>The payload is type-agnostic (all ten event types share the {@code #}
 * subscription), so it is logged as the deserialised object — but only at DEBUG:
 * it carries personal data, while INFO stays limited to the envelope. The
 * envelope fields are the ones logging really needs for correlation.</p>
 */
@Component
public class LoggingNotificationEventHandler implements NotificationEventHandler {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationEventHandler.class);

    @Override
    public void handle(DomainEvent<?> event) {
        Objects.requireNonNull(event, "event");
        if (log.isInfoEnabled()) {
            log.info("Domain event received: eventId={}, eventType={}, version={}, occurredAt={}",
                    event.eventId(), event.eventType(), event.version(), event.occurredAt());
        }
        if (log.isDebugEnabled()) {
            log.debug("Domain event payload: eventId={}, payload={}", event.eventId(), event.payload());
        }
    }
}
