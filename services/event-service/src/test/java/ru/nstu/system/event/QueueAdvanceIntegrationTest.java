package ru.nstu.system.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import ru.nstu.system.security.RoleNames;

/**
 * Task 8.4: "next" — the first waiting entry becomes passed, paused entries are
 * skipped, both domain events are written to the outbox, and empty/closed events
 * or insufficient roles are rejected (spec "Кнопка «Далее»").
 */
class QueueAdvanceIntegrationTest extends AbstractEventIntegrationTest {

    @Test
    void advancePassesFirstWaitingAndPublishesBothEvents() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Далее"));
        UUID firstId = joinQueueOk(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Первый");
        UUID secondId = joinQueueOk(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Второй");
        UUID staffId = UUID.randomUUID();

        MvcResult result = advance(eventId, staffToken(staffId));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = responseJson(result);
        assertThat(body.get("queue").size()).isEqualTo(1);
        assertThat(body.get("queue").get(0).get("id").asText()).isEqualTo(secondId.toString());
        assertThat(body.get("journal").size()).isEqualTo(1);
        JsonNode passed = body.get("journal").get(0);
        assertThat(passed.get("id").asText()).isEqualTo(firstId.toString());
        assertThat(passed.get("status").asText()).isEqualTo("PASSED");
        assertThat(passed.get("passedAt").isNull()).isFalse();

        assertThat(jdbcTemplate.queryForObject(
                "select status from event.queue_entry where id = ?", String.class, firstId))
                .isEqualTo("PASSED");
        assertThat(jdbcTemplate.queryForObject(
                "select passed_at from event.queue_entry where id = ?", Instant.class, firstId))
                .isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "select passed_by from event.queue_entry where id = ?", UUID.class, firstId))
                .isEqualTo(staffId);

        assertThat(outboxCountByType("entry.passed")).isEqualTo(1);
        assertThat(outboxCountByType("queue.advanced")).isEqualTo(1);
        JsonNode entryPassed = objectMapper.readTree(jdbcTemplate.queryForObject(
                "select payload::text from event.outbox where event_type = 'entry.passed'", String.class));
        assertThat(entryPassed.get("payload").get("entryId").asText()).isEqualTo(firstId.toString());
        assertThat(entryPassed.get("payload").get("passedBy").asText()).isEqualTo(staffId.toString());

        JsonNode advanced = objectMapper.readTree(jdbcTemplate.queryForObject(
                "select payload::text from event.outbox where event_type = 'queue.advanced'", String.class));
        assertThat(advanced.get("payload").get("passedEntryId").asText()).isEqualTo(firstId.toString());
        assertThat(advanced.get("payload").get("nextEntryId").asText()).isEqualTo(secondId.toString());
    }

    @Test
    void advanceOnSingleEntryLeavesNextEntryNull() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Далее"));
        joinQueueOk(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Единственный");

        assertThat(advance(eventId, staffToken()).getResponse().getStatus()).isEqualTo(200);

        JsonNode advanced = objectMapper.readTree(jdbcTemplate.queryForObject(
                "select payload::text from event.outbox where event_type = 'queue.advanced'", String.class));
        assertThat(advanced.get("payload").get("nextEntryId").isNull()).isTrue();
    }

    @Test
    void pausedEntryIsSkipped() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Далее"));
        UUID firstId = joinQueueOk(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Первый");
        UUID secondId = joinQueueOk(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Второй");
        assertThat(pause(eventId, firstId, staffToken()).getResponse().getStatus()).isEqualTo(200);

        MvcResult result = advance(eventId, staffToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode queue = responseJson(result).get("queue");
        assertThat(queue.size()).isEqualTo(1);
        assertThat(queue.get(0).get("id").asText()).isEqualTo(firstId.toString());
        assertThat(queue.get(0).get("status").asText()).isEqualTo("PAUSED");
        assertThat(jdbcTemplate.queryForObject(
                "select status from event.queue_entry where id = ?", String.class, secondId))
                .isEqualTo("PASSED");
    }

    @Test
    void emptyQueueIsRejected() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Далее"));

        MvcResult result = advance(eventId, staffToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(result)).isEqualTo("queue_empty");
        assertThat(outboxCount()).isZero();
    }

    @Test
    void closedEventIsRejected() throws Exception {
        UUID eventId = insertEvent("GUEST+", "CLOSED");

        MvcResult result = advance(eventId, staffToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(result)).isEqualTo("event_closed");
    }

    @Test
    void studentAndGuestCannotAdvance() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Далее"));
        joinQueueOk(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Первый");

        assertThat(advance(eventId, studentToken()).getResponse().getStatus()).isEqualTo(403);
        assertThat(advance(eventId, guestToken()).getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void unknownEventIsNotFound() throws Exception {
        assertThat(advance(UUID.randomUUID(), staffToken()).getResponse().getStatus()).isEqualTo(404);
    }
}
