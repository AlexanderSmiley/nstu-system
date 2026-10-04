package ru.nstu.system.contracts.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Jackson round-trip tests for the {@link DomainEvent} envelope: every event
 * type must survive serialisation with its id, type, version, timestamp and
 * payload intact.
 */
class DomainEventSerializationTest {

    private static final ObjectMapper MAPPER = EventJson.objectMapper();

    private static final Instant OCCURRED_AT = Instant.parse("2026-09-27T10:15:30Z");

    private static final UUID ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID EVENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ENTRY_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID STAFF_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID NEXT_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    static Stream<Arguments> events() {
        return Stream.of(
                Arguments.of(wrap(EventTypes.ACCOUNT_CREATED,
                        new AccountCreatedPayload(ACCOUNT_ID, "admin", "ADMIN", "Админ")),
                        AccountCreatedPayload.class),
                Arguments.of(wrap(EventTypes.ACCOUNT_UPDATED,
                        new AccountUpdatedPayload(ACCOUNT_ID, "staff", "STAFF", "Староста")),
                        AccountUpdatedPayload.class),
                Arguments.of(wrap(EventTypes.ACCOUNT_BLOCKED,
                        new AccountBlockedPayload(ACCOUNT_ID)),
                        AccountBlockedPayload.class),
                Arguments.of(wrap(EventTypes.ACCOUNT_UNBLOCKED,
                        new AccountUnblockedPayload(ACCOUNT_ID)),
                        AccountUnblockedPayload.class),
                Arguments.of(wrap(EventTypes.ACCOUNT_PASSWORD_RESET,
                        new AccountPasswordResetPayload(ACCOUNT_ID)),
                        AccountPasswordResetPayload.class),
                Arguments.of(wrap(EventTypes.PROFILE_UPDATED,
                        new ProfileUpdatedPayload(ACCOUNT_ID, "Иван Иванович")),
                        ProfileUpdatedPayload.class),
                Arguments.of(wrap(EventTypes.EVENT_CLOSED,
                        new EventClosedPayload(EVENT_ID, "slug-1", "Контрольная работа")),
                        EventClosedPayload.class),
                Arguments.of(wrap(EventTypes.ENTRY_PASSED,
                        new EntryPassedPayload(EVENT_ID, ENTRY_ID, "Бригада 1", OCCURRED_AT, STAFF_ID)),
                        EntryPassedPayload.class),
                Arguments.of(wrap(EventTypes.QUEUE_ADVANCED,
                        new QueueAdvancedPayload(EVENT_ID, ENTRY_ID, "Бригада 1", NEXT_ID)),
                        QueueAdvancedPayload.class),
                Arguments.of(wrap(EventTypes.EVENT_ARCHIVED,
                        new EventArchivedPayload(EVENT_ID)),
                        EventArchivedPayload.class));
    }

    private static <T> DomainEvent<T> wrap(String eventType, T payload) {
        return new DomainEvent<>(UUID.randomUUID(), eventType, EventTypes.CURRENT_VERSION,
                OCCURRED_AT, payload);
    }

    @ParameterizedTest(name = "{0} survives a JSON round-trip")
    @MethodSource("events")
    void roundTripPreservesEnvelopeAndPayload(DomainEvent<?> event, Class<?> payloadType)
            throws Exception {
        JavaType type = MAPPER.getTypeFactory()
                .constructParametricType(DomainEvent.class, payloadType);

        String json = MAPPER.writeValueAsString(event);
        DomainEvent<?> restored = MAPPER.readValue(json, type);

        assertThat(restored.eventId()).isEqualTo(event.eventId());
        assertThat(restored.eventType()).isEqualTo(event.eventType());
        assertThat(restored.version()).isEqualTo(EventTypes.CURRENT_VERSION);
        assertThat(restored.occurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(restored.payload()).isEqualTo(event.payload());
        assertThat(restored.payload()).isInstanceOf(payloadType);
    }

    @Test
    void writesTimestampsAsIsoStrings() throws Exception {
        DomainEvent<EventArchivedPayload> event = wrap(
                EventTypes.EVENT_ARCHIVED, new EventArchivedPayload(EVENT_ID));

        String json = MAPPER.writeValueAsString(event);

        assertThat(json).contains("2026-09-27T10:15:30Z");
        assertThat(json).doesNotContain("\"occurredAt\":1");
    }

    @Test
    void eventTypeConstantsAreComplete() {
        assertThat(EventTypes.CURRENT_VERSION).isEqualTo(1);
        assertThat(EventTypes.ALL).hasSize(10)
                .contains(EventTypes.ACCOUNT_CREATED, EventTypes.EVENT_ARCHIVED);
    }

    @Test
    void factoryFillsIdTypeAndVersion() {
        DomainEvent<AccountBlockedPayload> event =
                DomainEvent.of(EventTypes.ACCOUNT_BLOCKED, new AccountBlockedPayload(ACCOUNT_ID));

        assertThat(event.eventId()).isNotNull();
        assertThat(event.version()).isEqualTo(EventTypes.CURRENT_VERSION);
        assertThat(event.eventType()).isEqualTo(EventTypes.ACCOUNT_BLOCKED);
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    void eventTypesAreAlsoRoutingKeys() {
        List<String> routingKeys = List.copyOf(EventTypes.ALL);

        assertThat(routingKeys).allSatisfy(key -> assertThat(key).isNotBlank());
    }
}
