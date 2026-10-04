package ru.nstu.system.notification.messaging;

import java.util.Objects;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import ru.nstu.system.contracts.events.DomainEvent;
import ru.nstu.system.contracts.idempotency.IdempotentHandler;
import ru.nstu.system.notification.config.NotificationMessagingConfig;

/**
 * Subscribes to every domain event (task 10.1, design.md D22).
 *
 * <p>The queue is bound with {@code #}, so the listener is intentionally
 * type-agnostic: it accepts the shared {@link DomainEvent} envelope with an
 * untyped payload and forwards it to {@link NotificationEventHandler}.
 * Idempotency is delegated to {@link IdempotentHandler}: a redelivered
 * {@code eventId} is skipped, and a failure is turned into an
 * {@code AmqpRejectAndDontRequeueException} so the message is dead-lettered
 * rather than requeued forever.</p>
 */
@Component
public class DomainEventListener {

    private final IdempotentHandler idempotentHandler;

    private final NotificationEventHandler eventHandler;

    public DomainEventListener(IdempotentHandler idempotentHandler, NotificationEventHandler eventHandler) {
        this.idempotentHandler = Objects.requireNonNull(idempotentHandler, "idempotentHandler");
        this.eventHandler = Objects.requireNonNull(eventHandler, "eventHandler");
    }

    @RabbitListener(
            queues = NotificationMessagingConfig.NOTIFICATION_EVENTS_QUEUE,
            containerFactory = "notificationRabbitListenerContainerFactory")
    public void onDomainEvent(DomainEvent<?> event) {
        idempotentHandler.handle(event, () -> eventHandler.handle(event));
    }
}
