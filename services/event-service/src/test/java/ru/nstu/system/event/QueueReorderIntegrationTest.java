package ru.nstu.system.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import ru.nstu.system.security.RoleNames;

/**
 * Task 8.6: reordering active entries — the order persists, active entries are
 * renumbered {@code 1..K}, passed rows are untouched, and out-of-range positions,
 * passed entries or insufficient roles are rejected (spec "Перестановка записей").
 */
class QueueReorderIntegrationTest extends AbstractEventIntegrationTest {

    @Test
    void reorderRenumbersActiveEntries() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Порядок"));
        UUID firstId = joinQueueOk(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Первый");
        UUID secondId = joinQueueOk(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Второй");
        UUID thirdId = joinQueueOk(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Третий");

        MvcResult result = reorder(eventId, firstId, 3, staffToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode queue = responseJson(result).get("queue");
        assertThat(queue.size()).isEqualTo(3);
        assertThat(queue.get(0).get("id").asText()).isEqualTo(secondId.toString());
        assertThat(queue.get(1).get("id").asText()).isEqualTo(thirdId.toString());
        assertThat(queue.get(2).get("id").asText()).isEqualTo(firstId.toString());
        assertThat(queue.get(0).get("position").asInt()).isEqualTo(1);
        assertThat(queue.get(1).get("position").asInt()).isEqualTo(2);
        assertThat(queue.get(2).get("position").asInt()).isEqualTo(3);

        assertThat(positionOf(firstId)).isEqualTo(3);
        assertThat(positionOf(secondId)).isEqualTo(1);
        assertThat(positionOf(thirdId)).isEqualTo(2);
    }

    @Test
    void passedEntriesAreNotTouchedByReorder() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Порядок"));
        UUID passedId = joinQueueOk(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Сдавший");
        UUID waitingId = joinQueueOk(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Ожидающий");
        assertThat(advance(eventId, staffToken()).getResponse().getStatus()).isEqualTo(200);
        UUID lastId = joinQueueOk(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Последний");

        assertThat(reorder(eventId, lastId, 1, staffToken()).getResponse().getStatus()).isEqualTo(200);

        assertThat(statusOf(passedId)).isEqualTo("PASSED");
        assertThat(positionOf(passedId)).isEqualTo(1);
        assertThat(positionOf(lastId)).isEqualTo(1);
        assertThat(positionOf(waitingId)).isEqualTo(2);
    }

    @Test
    void positionOutOfRangeIsRejected() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Порядок"));
        UUID firstId = joinQueueOk(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Первый");
        joinQueueOk(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Второй");

        MvcResult tooHigh = reorder(eventId, firstId, 5, staffToken());
        assertThat(tooHigh.getResponse().getStatus()).isEqualTo(400);
        assertThat(errorCode(tooHigh)).isEqualTo("invalid_position");

        MvcResult tooLow = reorder(eventId, firstId, 0, staffToken());
        assertThat(tooLow.getResponse().getStatus()).isEqualTo(400);
        assertThat(errorCode(tooLow)).isEqualTo("invalid_position");
        assertThat(positionOf(firstId)).isEqualTo(1);
    }

    @Test
    void passedEntryCannotBeReordered() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Порядок"));
        UUID entryId = joinQueueOk(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Сдавший");
        assertThat(advance(eventId, staffToken()).getResponse().getStatus()).isEqualTo(200);

        MvcResult result = reorder(eventId, entryId, 1, staffToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(result)).isEqualTo("invalid_state");
    }

    @Test
    void studentCannotReorder() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Порядок"));
        UUID firstId = joinQueueOk(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Первый");
        joinQueueOk(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Второй");

        assertThat(reorder(eventId, firstId, 2, studentToken()).getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void unknownEntryIsNotFound() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Порядок"));

        assertThat(reorder(eventId, UUID.randomUUID(), 1, staffToken()).getResponse().getStatus())
                .isEqualTo(404);
    }

    @Test
    void entryOfAnotherEventIsNotFound() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Первый"));
        UUID otherEventId = createEvent(staffToken(), Map.of("title", "Второй"));
        UUID entryId = joinQueueOk(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Первый");

        assertThat(reorder(otherEventId, entryId, 1, staffToken()).getResponse().getStatus())
                .isEqualTo(404);
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
