package ru.nstu.system.contracts.events;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Shared, pre-configured Jackson mapper for domain events.
 *
 * <p>Serialises {@link java.time.Instant} as ISO-8601 strings (never as epoch
 * numbers) so that payloads stay readable in the outbox table and in RabbitMQ
 * management. A single instance is exposed because {@link ObjectMapper} is
 * thread-safe once configured.</p>
 */
public final class EventJson {

    private static final ObjectMapper MAPPER = createMapper();

    private EventJson() {
    }

    /** @return the shared mapper; do not reconfigure it at runtime */
    public static ObjectMapper objectMapper() {
        return MAPPER;
    }

    private static ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
        return mapper;
    }
}
