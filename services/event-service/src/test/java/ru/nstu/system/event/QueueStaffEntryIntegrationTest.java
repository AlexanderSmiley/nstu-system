package ru.nstu.system.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import ru.nstu.system.security.RoleNames;

/**
 * Task 9.3: staff-entered stubs ({@code origin = STAFF}, no account, no guest
 * session) that occupy the name (spec "Записи, созданные персоналом за
 * участника").
 */
class QueueStaffEntryIntegrationTest extends AbstractEventIntegrationTest {

    @Test
    void staffCreatesAStubAtTheEndOfTheQueue() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Заглушка"));
        joinQueueOk(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Первый");

        MvcResult result = addStaffEntry(eventId, staffToken(), "Бригада без аккаунта");

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        JsonNode body = responseJson(result);
        assertThat(body.get("name").asText()).isEqualTo("Бригада без аккаунта");
        assertThat(body.get("status").asText()).isEqualTo("WAITING");
        assertThat(body.get("origin").asText()).isEqualTo("STAFF");
        assertThat(body.get("holderAccountId").isNull()).isTrue();
        assertThat(body.get("guestRef").isNull()).isTrue();
        assertThat(body.get("position").asInt()).isEqualTo(2);
    }

    @Test
    void stubOccupiesTheNameForGuests() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Занято"));
        assertThat(addStaffEntry(eventId, staffToken(), "Занятое имя").getResponse().getStatus())
                .isEqualTo(201);

        MvcResult guest = joinQueue(eventId, guestToken(), "Занятое имя");

        assertThat(guest.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(guest)).isEqualTo("name_taken");
    }

    @Test
    void studentCannotCreateAStub() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Только персонал"));

        MvcResult result = addStaffEntry(eventId, studentToken(), "Имя");

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void blankNameIsRejected() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Имя"));

        MvcResult result = addStaffEntry(eventId, staffToken(), "   ");

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(errorCode(result)).isEqualTo("invalid_name");
    }

    @Test
    void entryLimitIsRespected() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Лимит", "entryLimit", 1));
        assertThat(addStaffEntry(eventId, staffToken(), "Первый").getResponse().getStatus())
                .isEqualTo(201);

        MvcResult second = addStaffEntry(eventId, staffToken(), "Второй");

        assertThat(second.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(second)).isEqualTo("queue_full");
    }

    @Test
    void closedEventRejectsAStaffEntry() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Закрытое"));
        assertThat(closeEvent(eventId, staffToken()).getResponse().getStatus()).isEqualTo(200);

        MvcResult result = addStaffEntry(eventId, staffToken(), "Поздно");

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(result)).isEqualTo("event_closed");
    }
}
