package ru.nstu.system.contracts.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import ru.nstu.system.contracts.events.EventJson;

/**
 * Shared RabbitMQ wiring imported explicitly by services ({@code @Import}).
 *
 * <p>Declares the common topology owned by the library: the {@code nstu.events}
 * topic exchange, the dead-letter exchange/queue pair and their binding. The
 * connection itself is never configured here: host, port and credentials come
 * from {@code spring.rabbitmq.*} through Spring Boot auto-configuration.</p>
 *
 * <p>Consumer services declare their own durable queue
 * ({@link NstuQueueNames#forService(String)}) with
 * {@code x-dead-letter-exchange = }{@link RabbitNames#DLX} and register their
 * own {@code @RabbitListener}s.</p>
 *
 * <p>This configuration is <em>not</em> registered through
 * {@code META-INF/spring/*.imports}: services opt in explicitly, which keeps
 * the reactive gateway free of AMQP beans and makes the wiring traceable.</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableRabbit
@EnableScheduling
public class NstuRabbitConfig {

    private static final Logger log = LoggerFactory.getLogger(NstuRabbitConfig.class);

    /** Trusted packages for the strict JSON type mapper. */
    static final String TRUSTED_PACKAGE = "ru.nstu.system.contracts";

    @Bean
    public TopicExchange nstuEventsExchange() {
        return new TopicExchange(RabbitNames.EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange nstuEventsDeadLetterExchange() {
        return new TopicExchange(RabbitNames.DLX, true, false);
    }

    @Bean
    public Queue nstuEventsDeadLetterQueue() {
        return QueueBuilder.durable(RabbitNames.DLQ).build();
    }

    @Bean
    public Binding nstuEventsDeadLetterBinding(
            @Qualifier("nstuEventsDeadLetterQueue") Queue deadLetterQueue,
            @Qualifier("nstuEventsDeadLetterExchange") TopicExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue)
                .to(deadLetterExchange)
                .with(RabbitNames.MATCH_ALL_ROUTING_KEY);
    }

    /**
     * Strict JSON converter: dates as ISO-8601, unknown payload properties
     * rejected and the listener signature (not the {@code __TypeId__} header)
     * governing deserialization, so a foreign message can never instantiate an
     * arbitrary class.
     */
    @Bean
    public MessageConverter jackson2JsonMessageConverter() {
        Jackson2JsonMessageConverter converter =
                new Jackson2JsonMessageConverter(EventJson.objectMapper());
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTrustedPackages(TRUSTED_PACKAGE);
        typeMapper.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(
            ConnectionFactory connectionFactory,
            MessageConverter jackson2JsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jackson2JsonMessageConverter);
        // Unroutable messages must surface instead of being silently dropped.
        template.setMandatory(true);
        template.setReturnsCallback(returned -> log.error(
                "Unroutable event: exchange={}, routingKey={}, replyText={}",
                returned.getExchange(), returned.getRoutingKey(), returned.getReplyText()));
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                String id = correlationData == null ? "<unknown>" : correlationData.getId();
                log.error("Broker rejected event {}: {}", id, cause);
            }
        });
        return template;
    }

    /**
     * Listener container factory used by every {@code @RabbitListener} in the
     * services (the bean name is the one Spring AMQP looks up by default).
     *
     * <p>{@code defaultRequeueRejected = false} is the safety net required by
     * design.md D13: an exception raised by a listener rejects the message and
     * dead-letters it instead of requeueing it forever, so a poisoned message
     * cannot pin a consumer.</p>
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter jackson2JsonMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jackson2JsonMessageConverter);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }

    @Bean
    public EventPublisher eventPublisher(RabbitTemplate rabbitTemplate) {
        return new RabbitEventPublisher(rabbitTemplate);
    }
}
