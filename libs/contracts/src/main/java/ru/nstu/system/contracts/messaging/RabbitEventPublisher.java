package ru.nstu.system.contracts.messaging;

import java.util.Objects;
import java.util.UUID;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import ru.nstu.system.contracts.events.DomainEvent;

/**
 * {@link EventPublisher} backed by {@link RabbitTemplate}.
 *
 * <p>Serialises the event with the shared {@code jackson2JsonMessageConverter},
 * sends it to {@link RabbitNames#EXCHANGE} with the event type as routing key,
 * and passes the event id as the correlation id so publisher confirms can be
 * correlated back to outbox rows.</p>
 */
public class RabbitEventPublisher implements EventPublisher {

    private final RabbitTemplate rabbitTemplate;

    private final String exchange;

    public RabbitEventPublisher(RabbitTemplate rabbitTemplate) {
        this(rabbitTemplate, RabbitNames.EXCHANGE);
    }

    public RabbitEventPublisher(RabbitTemplate rabbitTemplate, String exchange) {
        this.rabbitTemplate = Objects.requireNonNull(rabbitTemplate, "rabbitTemplate");
        this.exchange = Objects.requireNonNull(exchange, "exchange");
    }

    @Override
    public UUID publish(DomainEvent<?> event) {
        Objects.requireNonNull(event, "event");
        UUID eventId = event.eventId();
        CorrelationData correlationData = new CorrelationData(eventId.toString());
        rabbitTemplate.convertAndSend(exchange, event.eventType(), event, correlationData);
        return eventId;
    }
}
