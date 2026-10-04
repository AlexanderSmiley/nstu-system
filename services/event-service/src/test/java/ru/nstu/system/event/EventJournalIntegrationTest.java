package ru.nstu.system.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import ru.nstu.system.security.RoleNames;

/**
 * Task 9.1: the surrender journal in the event detail response.
 *
 * <p>{@code GET /api/events/{id}} returns the event together with the journal
 * (same entry DTO as {@code GET /api/events/{id}/queue}); the journal key is
 * absent for a non-staff caller when the event hides it (spec "Журнал сдач",
 * "Видимость журнала сдач").
 */
class EventJournalIntegrationTest extends AbstractEventIntegrationTest {

    @Test
    void staffSeesTheJournalInTheEventDetail() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Журнал"));
        joinQueueOk(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Первый");
        assertThat(advance(eventId, staffToken()).getResponse().getStatus()).isEqualTo(200);

        MvcResult result = getEvent(eventId, staffToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = responseJson(result);
        assertThat(body.get("id").asText()).isEqualTo(eventId.toString());
        JsonNode journal = body.get("journal");
        assertThat(journal).isNotNull();
        assertThat(journal.size()).isEqualTo(1);
        assertThat(journal.get(0).get("status").asText()).isEqualTo("PASSED");
        assertThat(journal.get(0).get("passedAt").isNull()).isFalse();
    }

    @Test
    void journalIsAbsentForStudentWhenVisibilityIsStaff() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Скрытый журнал"));
        joinQueueOk(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Первый");
        assertThat(advance(eventId, staffToken()).getResponse().getStatus()).isEqualTo(200);

        MvcResult result = getEvent(eventId, studentToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = responseJson(result);
        assertThat(body.has("journal")).isFalse();
        assertThat(body.get("title").asText()).isEqualTo("Скрытый журнал");
    }

    @Test
    void journalIsVisibleToStudentWhenVisibilityIsEveryone() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of(
                "title", "Открытый журнал", "journalVisibility", "EVERYONE"));
        joinQueueOk(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Первый");
        assertThat(advance(eventId, staffToken()).getResponse().getStatus()).isEqualTo(200);

        MvcResult result = getEvent(eventId, studentToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode journal = responseJson(result).get("journal");
        assertThat(journal).isNotNull();
        assertThat(journal.size()).isEqualTo(1);
    }

    @Test
    void unknownEventDetailIsNotFound() throws Exception {
        MvcResult result = getEvent(UUID.randomUUID(), staffToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(errorCode(result)).isEqualTo("event_not_found");
    }

    @Test
    void detailRespectsEventAvailability() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of(
                "title", "Только персонал", "availability", "STAFF+"));

        assertThat(getEvent(eventId, studentToken()).getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void archivedEventDetailIsNotFound() throws Exception {
        UUID eventId = insertEvent("GUEST+", "ARCHIVED");

        MvcResult result = getEvent(eventId, staffToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(errorCode(result)).isEqualTo("event_not_found");
    }
}
