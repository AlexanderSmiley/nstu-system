package ru.nstu.system.e2e.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import ru.nstu.system.contracts.messaging.RabbitNames;

/**
 * Captures every domain event published to the {@code nstu.events} topic exchange
 * (OpenSpec task 12.1, checks 7 and 10).
 *
 * <p>It declares a private, non-durable queue bound with the {@code #} routing key
 * and consumes it, recording the routing key and the raw envelope of every message.
 * Because the probe starts before the services, it observes the events the outbox
 * publisher actually puts on the broker — not merely the committed outbox rows.</p>
 *
 * <p>It intentionally does not rely on {@code notification-service} running: the
 * assertion target is the broker, and an independent listener makes the check
 * deterministic regardless of how fast the consumer drains its own queue.</p>
 */
public final class RabbitEventProbe implements AutoCloseable {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    private final CachingConnectionFactory connectionFactory;

    private final RabbitAdmin admin;

    private final SimpleMessageListenerContainer container;

    private final String queueName;

    private final List<CapturedEvent> captured = new CopyOnWriteArrayList<>();

    /** Declares the probe exchange/queue/binding and starts consuming. */
    public RabbitEventProbe(String host, int port, String username, String password) {
        this.connectionFactory = new CachingConnectionFactory(host, port);
        this.connectionFactory.setUsername(username);
        this.connectionFactory.setPassword(password);

        this.admin = new RabbitAdmin(connectionFactory);
        TopicExchange exchange = new TopicExchange(RabbitNames.EXCHANGE, true, false);
        this.queueName = "nstu.e2e.probe." + UUID.randomUUID();
        Queue queue = QueueBuilder.nonDurable(queueName).autoDelete().build();

        admin.declareExchange(exchange);
        admin.declareQueue(queue);
        admin.declareBinding(BindingBuilder.bind(queue).to(exchange).with(RabbitNames.MATCH_ALL_ROUTING_KEY));

        this.container = new SimpleMessageListenerContainer(connectionFactory);
        container.setQueueNames(queueName);
        container.setMessageListener((MessageListener) message -> captured.add(new CapturedEvent(
                message.getMessageProperties().getReceivedRoutingKey(),
                new String(message.getBody(), StandardCharsets.UTF_8))));
        container.start();
    }

    /** @return all captured events, in arrival order */
    public List<CapturedEvent> events() {
        return List.copyOf(captured);
    }

    /** @return captured events whose routing key equals the given type */
    public List<CapturedEvent> events(String routingKey) {
        return captured.stream().filter(event -> event.routingKey().equals(routingKey)).toList();
    }

    public boolean hasEvent(String routingKey) {
        return !events(routingKey).isEmpty();
    }

    public Optional<CapturedEvent> first(String routingKey) {
        return events(routingKey).stream().findFirst();
    }

    @Override
    public void close() {
        try {
            container.stop();
        } catch (RuntimeException ignored) {
            // Stopping a container whose broker is already gone is not an error.
        }
        try {
            admin.deleteQueue(queueName);
        } catch (RuntimeException ignored) {
            // Auto-delete queue; the broker may already have removed it.
        }
        connectionFactory.destroy();
    }

    /** One captured AMQP message. */
    public record CapturedEvent(String routingKey, String body) {

        /** @return the {@code DomainEvent} envelope parsed from the body */
        public JsonNode json() {
            try {
                return JSON.readTree(body);
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("event body is not valid JSON: " + body, ex);
            }
        }

        /** @return the value of the envelope's {@code eventType} field */
        public String eventType() {
            return json().path("eventType").asText();
        }
    }
}
