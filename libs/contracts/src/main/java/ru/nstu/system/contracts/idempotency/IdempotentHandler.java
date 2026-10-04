package ru.nstu.system.contracts.idempotency;

import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import ru.nstu.system.contracts.events.DomainEvent;

/**
 * Small base helper for idempotent consumers.
 *
 * <p>{@link #handle(DomainEvent, Runnable)} skips events already recorded by the
 * {@link IdempotencyGuard}, runs the action and marks the event handled only
 * after the action succeeds. If the action fails, the exception is wrapped into
 * {@link AmqpRejectAndDontRequeueException}: the broker rejects the message
 * (routing it to the dead-letter queue) instead of requeueing it forever, so a
 * poisoned message cannot block the consumer.</p>
 *
 * <p>Combined with the default RabbitMQ listener semantics this yields
 * at-least-once delivery: a crash between the action and the mark leads to one
 * extra execution, which the consumer must tolerate.</p>
 */
public class IdempotentHandler {

    private static final Logger log = LoggerFactory.getLogger(IdempotentHandler.class);

    private final IdempotencyGuard idempotencyGuard;

    public IdempotentHandler(IdempotencyGuard idempotencyGuard) {
        this.idempotencyGuard = Objects.requireNonNull(idempotencyGuard, "idempotencyGuard");
    }

    /**
     * Handles an event exactly once per guard state.
     *
     * @param event  received event
     * @param action domain action to run for a not-yet-processed event
     */
    public void handle(DomainEvent<?> event, Runnable action) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(action, "action");
        UUID eventId = event.eventId();
        if (idempotencyGuard.alreadyProcessed(eventId)) {
            log.debug("Skipping already processed event {} ({})", eventId, event.eventType());
            return;
        }
        try {
            action.run();
        } catch (RuntimeException e) {
            log.error("Failed to handle event {} ({}); rejecting to DLQ",
                    eventId, event.eventType(), e);
            throw new AmqpRejectAndDontRequeueException(
                    "failed to handle event " + eventId, e);
        }
        idempotencyGuard.markProcessed(eventId);
    }
}
