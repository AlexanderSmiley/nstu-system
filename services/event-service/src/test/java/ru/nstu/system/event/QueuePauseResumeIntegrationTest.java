package ru.nstu.system.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import ru.nstu.system.security.RoleNames;

/**
 * Task 8.5: pausing and resuming entries — the position is preserved, a paused
 * entry still occupies the limit, and invalid transitions or roles are rejected
 * (spec "Приостановка записи", "Возврат записи из приостановки").
 */
class QueuePauseResumeIntegrationTest extends AbstractEventIntegrationTest {

    @Test
    void pauseAndResumeKeepTheOriginalPosition() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Пауза"));
        joinQueueOk(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Первый");
        UUID secondId = joinQueueOk(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Второй");
        joinQueueOk(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Третий");

        MvcResult paused = pause(eventId, secondId, staffToken());
        assertThat(paused.getResponse().getStatus()).isEqualTo(200);
        assertThat(statusOf(secondId)).isEqualTo("PAUSED");
        assertThat(positionOf(secondId)).isEqualTo(2);

        MvcResult resumed = resume(eventId, secondId, staffToken());
        assertThat(resumed.getResponse().getStatus()).isEqualTo(200);
        assertThat(statusOf(secondId)).isEqualTo("WAITING");
        assertThat(positionOf(secondId)).isEqualTo(2);

        JsonNode queue = responseJson(resumed).get("queue");
        assertThat(queue.size()).isEqualTo(3);
        assertThat(queue.get(1).get("id").asText()).isEqualTo(secondId.toString());
    }

    @Test
    void pausedEntryStillOccupiesTheLimit() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Лимит", "entryLimit", 2));
        UUID firstId = joinQueueOk(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Первый");
        joinQueueOk(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Второй");
        assertThat(pause(eventId, firstId, staffToken()).getResponse().getStatus()).isEqualTo(200);

        MvcResult result = joinQueue(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Третий");

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(result)).isEqualTo("queue_full");
    }

    @Test
    void pausedEntryIsFreedAfterRemoval() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Лимит", "entryLimit", 2));
        UUID firstId = joinQueueOk(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Первый");
        joinQueueOk(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Второй");
        assertThat(pause(eventId, firstId, staffToken()).getResponse().getStatus()).isEqualTo(200);
        assertThat(deleteEntry(eventId, firstId, staffToken()).getResponse().getStatus()).isEqualTo(204);

        MvcResult result = joinQueue(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Третий");

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void cannotPausePassedEntry() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Пауза"));
        UUID entryId = joinQueueOk(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Первый");
        assertThat(advance(eventId, staffToken()).getResponse().getStatus()).isEqualTo(200);

        MvcResult result = pause(eventId, entryId, staffToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(result)).isEqualTo("invalid_state");
    }

    @Test
    void cannotResumeWaitingEntry() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Пауза"));
        UUID entryId = joinQueueOk(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Первый");

        MvcResult result = resume(eventId, entryId, staffToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(result)).isEqualTo("invalid_state");
    }

    @Test
    void studentAndGuestCannotChangeStatus() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Пауза"));
        UUID entryId = joinQueueOk(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Первый");

        assertThat(pause(eventId, entryId, studentToken()).getResponse().getStatus()).isEqualTo(403);
        assertThat(resume(eventId, entryId, guestToken()).getResponse().getStatus()).isEqualTo(403);
        assertThat(statusOf(entryId)).isEqualTo("WAITING");
    }

    @Test
    void unknownEntryIsNotFound() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Пауза"));

        assertThat(pause(eventId, UUID.randomUUID(), staffToken()).getResponse().getStatus()).isEqualTo(404);
    }

    private String statusOf(UUID entryId) {
        return jdbcTemplate.queryForObject(
                "select status from event.queue_entry where id = ?", String.class, entryId);
    }

    private int positionOf(UUID entryId) {
        Integer position = jdbcTemplate.queryForObject(
                "select position from event.queue_entry where id = ?", Integer.class, entryId);
        return position == null ? -1 : position;
    }
}
