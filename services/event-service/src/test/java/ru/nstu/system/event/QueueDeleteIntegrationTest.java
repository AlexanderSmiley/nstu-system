package ru.nstu.system.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import ru.nstu.system.security.RoleNames;

/**
 * Task 8.7: staff removal and self-exit — the name is freed, a re-join goes to
 * the end, a participant may only delete their own entry, and passed rows cannot
 * be deleted (spec "Удаление записи персоналом", "Самостоятельный выход").
 */
class QueueDeleteIntegrationTest extends AbstractEventIntegrationTest {

    @Test
    void staffDeleteFreesTheNameAndRejoinGoesToTheEnd() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Удаление"));
        UUID studentId = UUID.randomUUID();
        String token = tokenFor(studentId, RoleNames.STUDENT);
        UUID firstId = joinQueueOk(eventId, token, "Аня");
        joinQueueOk(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Борис");

        MvcResult deleted = deleteEntry(eventId, firstId, staffToken());

        assertThat(deleted.getResponse().getStatus()).isEqualTo(204);
        assertThat(activeEntryCount(eventId)).isEqualTo(1);

        MvcResult rejoined = joinQueue(eventId, token, "Аня");

        assertThat(rejoined.getResponse().getStatus()).isEqualTo(201);
        JsonNode body = responseJson(rejoined);
        UUID newEntryId = UUID.fromString(body.get("id").asText());
        assertThat(newEntryId).isNotEqualTo(firstId);
        assertThat(body.get("position").asInt()).isEqualTo(3);
    }

    @Test
    void participantDeletesOwnEntry() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Своя"));
        String token = tokenFor(UUID.randomUUID(), RoleNames.STUDENT);
        UUID entryId = joinQueueOk(eventId, token, "Своя");

        MvcResult result = deleteEntry(eventId, entryId, token);

        assertThat(result.getResponse().getStatus()).isEqualTo(204);
        assertThat(activeEntryCount(eventId)).isZero();
    }

    @Test
    void participantCannotDeleteForeignEntry() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Чужая"));
        UUID firstId = joinQueueOk(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Первый");
        String secondToken = tokenFor(UUID.randomUUID(), RoleNames.STUDENT);
        joinQueueOk(eventId, secondToken, "Второй");

        MvcResult result = deleteEntry(eventId, firstId, secondToken);

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(activeEntryCount(eventId)).isEqualTo(2);
    }

    @Test
    void staffCannotDeletePassedEntry() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Журнал"));
        UUID entryId = joinQueueOk(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Сдавший");
        assertThat(advance(eventId, staffToken()).getResponse().getStatus()).isEqualTo(200);

        MvcResult result = deleteEntry(eventId, entryId, staffToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(result)).isEqualTo("invalid_state");
    }

    @Test
    void guestDeletesOwnEntry() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Гость"));
        String guest = guestToken();
        UUID entryId = joinQueueOk(eventId, guest, "Гость");

        assertThat(deleteEntry(eventId, entryId, guest).getResponse().getStatus()).isEqualTo(204);
        assertThat(activeEntryCount(eventId)).isZero();
    }

    @Test
    void guestCannotDeleteForeignEntry() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Гость"));
        UUID entryId = joinQueueOk(eventId, guestToken(), "Первый гость");

        MvcResult result = deleteEntry(eventId, entryId, guestToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(activeEntryCount(eventId)).isEqualTo(1);
    }

    @Test
    void unknownEntryIsNotFound() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Удаление"));

        assertThat(deleteEntry(eventId, UUID.randomUUID(), staffToken()).getResponse().getStatus())
                .isEqualTo(404);
    }
}
