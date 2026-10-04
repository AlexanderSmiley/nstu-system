package ru.nstu.system.student.messaging;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.nstu.system.contracts.events.EventTypes;
import ru.nstu.system.contracts.messaging.RabbitNames;

/**
 * Re-drives messages from the shared dead-letter queue back to the topic exchange
 * (design.md D13, task 6.2).
 *
 * <p>A listener failure either exhausts the in-memory retries or is rejected
 * outright, which dead-letters the message. This scheduler periodically moves
 * those messages back to {@link RabbitNames#EXCHANGE} with their original routing
 * key, so a transient outage (broker or database) does not lose the event: once
 * the cause is gone, the next attempt succeeds. Idempotency keeps the replay
 * safe.</p>
 *
 * <p>{@link #reDrive()} is public and idempotent so tests can trigger one
 * re-drive pass deterministically instead of waiting for the schedule. The
 * interval comes from {@code nstu.dlq.redrive-interval}.</p>
 */
@Component
public class DlqReDriveScheduler {

    private static final Logger log = LoggerFactory.getLogger(DlqReDriveScheduler.class);

    private final RabbitTemplate rabbitTemplate;

    private final int maxBatch;

    private final Duration receiveTimeout;

    public DlqReDriveScheduler(
            RabbitTemplate rabbitTemplate,
            @Value("${nstu.dlq.redrive-max-batch:100}") int maxBatch,
            @Value("${nstu.dlq.receive-timeout:200ms}") Duration receiveTimeout) {
        this.rabbitTemplate = Objects.requireNonNull(rabbitTemplate, "rabbitTemplate");
        this.maxBatch = maxBatch;
        this.receiveTimeout = Objects.requireNonNull(receiveTimeout, "receiveTimeout");
    }

    @Scheduled(fixedDelayString = "${nstu.dlq.redrive-interval:PT1M}")
    public void scheduledReDrive() {
        reDrive();
    }

    /**
     * Moves up to {@link #maxBatch} currently available DLQ messages back to the
     * exchange.
     *
     * @return the number of messages moved in this pass
     */
    public int reDrive() {
        int moved = 0;
        try {
            Message message;
            while (moved < maxBatch
                    && (message = rabbitTemplate.receive(RabbitNames.DLQ, receiveTimeout.toMillis())) != null) {
                rePublish(message);
                moved++;
            }
        } catch (AmqpException ex) {
            // Broker unavailable: keep the messages where they are and retry later.
            log.warn("DLQ re-drive skipped: broker unavailable ({})", ex.getMessage());
            return moved;
        }
        if (moved > 0) {
            log.info("Re-drove {} message(s) from {} back to {}", moved, RabbitNames.DLQ, RabbitNames.EXCHANGE);
        }
        return moved;
    }

    private void rePublish(Message message) {
        String routingKey = routingKey(message);
        rabbitTemplate.send(RabbitNames.EXCHANGE, routingKey, reDriven(message));
    }

    private static String routingKey(Message message) {
        String receivedRoutingKey = message.getMessageProperties().getReceivedRoutingKey();
        if (receivedRoutingKey != null && !receivedRoutingKey.isBlank()) {
            return receivedRoutingKey;
        }
        // Should not happen for messages dead-lettered by this service.
        return EventTypes.ACCOUNT_CREATED;
    }

    /**
     * Builds a clean message: consumer-side properties ({@code deliveryTag},
     * {@code received*}) and the broker-managed {@code x-death} headers are dropped,
     * while the body, content type and application headers (including the type
     * information used by the JSON converter) are preserved.
     */
    private static Message reDriven(Message message) {
        MessageProperties original = message.getMessageProperties();
        MessageProperties properties = new MessageProperties();
        properties.setContentType(original.getContentType());
        properties.setContentEncoding(original.getContentEncoding());
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        if (original.getCorrelationId() != null) {
            properties.setCorrelationId(original.getCorrelationId());
        }
        Map<String, Object> headers = new HashMap<>(original.getHeaders());
        headers.keySet().removeIf(key -> key.startsWith("x-death") || key.startsWith("x-first-death"));
        properties.getHeaders().putAll(headers);
        return new Message(message.getBody(), properties);
    }
}
