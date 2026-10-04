package ru.nstu.system.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import ru.nstu.system.security.RoleNames;

/**
 * Task 8.1/8.2: joining the queue — account/guest binding, name uniqueness,
 * repeat-join, entry limit and closed events (spec "Вступление в очередь").
 */
class QueueJoinIntegrationTest extends AbstractEventIntegrationTest {

    @Test
    void studentWithExplicitNameJoinsAtFirstPosition() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Очередь"));
        UUID studentId = UUID.randomUUID();

        MvcResult result = joinQueue(eventId, tokenFor(studentId, RoleNames.STUDENT), "Иванов Иван");

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        JsonNode body = responseJson(result);
        UUID entryId = UUID.fromString(body.get("id").asText());
        assertThat(body.get("position").asInt()).isEqualTo(1);
        assertThat(body.get("status").asText()).isEqualTo("WAITING");
        assertThat(body.get("origin").asText()).isEqualTo("JOIN");
        assertThat(body.get("name").asText()).isEqualTo("Иванов Иван");
        assertThat(body.get("holderAccountId").asText()).isEqualTo(studentId.toString());
        assertThat(body.get("guestRef").isNull()).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "select holder_account_id from event.queue_entry where id = ?", UUID.class, entryId))
                .isEqualTo(studentId);
        assertThat(jdbcTemplate.queryForObject(
                "select name_normalized from event.queue_entry where id = ?", String.class, entryId))
                .isEqualTo("иванов иван");
    }

    @Test
    void guestJoinsAndGuestRefIsFilled() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Для гостей"));

        MvcResult result = joinQueue(eventId, guestToken(), "Гость Пётр");

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        JsonNode body = responseJson(result);
        UUID entryId = UUID.fromString(body.get("id").asText());
        assertThat(body.get("holderAccountId").isNull()).isTrue();
        assertThat(body.get("guestRef").isNull()).isFalse();
        UUID guestRef = UUID.fromString(body.get("guestRef").asText());
        assertThat(jdbcTemplate.queryForObject(
                "select guest_ref from event.queue_entry where id = ?", UUID.class, entryId))
                .isEqualTo(guestRef);
    }

    @Test
    void guestWithMissingNameIsRejected() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Для гостей"));

        MvcResult result = joinQueue(eventId, guestToken(), null);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(errorCode(result)).isEqualTo("invalid_name");
        assertThat(activeEntryCount(eventId)).isZero();
    }

    @Test
    void guestWithBlankNameIsRejected() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Для гостей"));

        MvcResult result = joinQueue(eventId, guestToken(), "    ");

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(errorCode(result)).isEqualTo("invalid_name");
    }

    @Test
    void nameTakenIgnoringCase() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Уникальность"));
        joinQueueOk(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Иванов");

        MvcResult result = joinQueue(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "иванов  ");

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(result)).isEqualTo("name_taken");
    }

    @Test
    void sameAccountCannotJoinTwice() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Повтор"));
        String token = tokenFor(UUID.randomUUID(), RoleNames.STUDENT);
        joinQueueOk(eventId, token, "Первое имя");

        MvcResult result = joinQueue(eventId, token, "Второе имя");

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(result)).isEqualTo("already_joined");
    }

    @Test
    void sameGuestSessionCannotJoinTwice() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Повтор гостя"));
        String guest = guestToken();
        joinQueueOk(eventId, guest, "Гость");

        MvcResult result = joinQueue(eventId, guest, "Гость снова");

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(result)).isEqualTo("already_joined");
    }

    @Test
    void entryLimitIsEnforcedForActiveEntries() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Лимит", "entryLimit", 2));
        joinQueueOk(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Первый");
        joinQueueOk(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Второй");

        MvcResult result = joinQueue(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Третий");

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(result)).isEqualTo("queue_full");
        assertThat(activeEntryCount(eventId)).isEqualTo(2);
    }

    @Test
    void joiningAClosedEventIsRejected() throws Exception {
        UUID eventId = insertEvent("GUEST+", "CLOSED");

        MvcResult result = joinQueue(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Поздний");

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(result)).isEqualTo("event_closed");
    }

    @Test
    void joiningAnArchivedEventIsRejected() throws Exception {
        UUID eventId = insertEvent("GUEST+", "ARCHIVED");

        MvcResult result = joinQueue(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Поздний");

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(result)).isEqualTo("event_closed");
    }

    @Test
    void guestCannotJoinStaffOnlyEvent() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Только персонал", "availability", "STAFF+"));

        MvcResult result = joinQueue(eventId, guestToken(), "Гость");

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(activeEntryCount(eventId)).isZero();
    }

    @Test
    void unknownEventIsNotFound() throws Exception {
        MvcResult result = joinQueue(UUID.randomUUID(), studentToken(), "Имя");

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(errorCode(result)).isEqualTo("event_not_found");
    }

    @Test
    void tooLongNameIsRejected() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Длина"));

        MvcResult result = joinQueue(eventId, studentToken(), "a".repeat(121));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(errorCode(result)).isEqualTo("invalid_name");
    }

    @Test
    void entriesReceiveConsecutivePositions() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Позиции"));

        UUID first = UUID.fromString(responseJson(
                joinQueue(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Первый"))
                .get("id").asText());
        assertThat(positionOf(first)).isEqualTo(1);

        MvcResult second = joinQueue(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Второй");
        assertThat(responseJson(second).get("position").asInt()).isEqualTo(2);
    }

    private int positionOf(UUID entryId) {
        Integer position = jdbcTemplate.queryForObject(
                "select position from event.queue_entry where id = ?", Integer.class, entryId);
        return position == null ? -1 : position;
    }
}
