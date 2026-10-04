package ru.nstu.system.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import ru.nstu.system.security.RoleNames;

/**
 * Task 8.8: the queue/journal projection — journal visibility by event setting,
 * deterministic {@code ETag} with conditional {@code 304}, and access checks
 * (spec "Порядок очереди", "Журнал сдач"; design.md D20).
 */
class QueueStateIntegrationTest extends AbstractEventIntegrationTest {

    @Test
    void journalIsHiddenFromStudentsWhenVisibilityIsStaff() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Журнал"));
        joinQueueOk(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Первый");
        joinQueueOk(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Второй");
        assertThat(advance(eventId, staffToken()).getResponse().getStatus()).isEqualTo(200);

        MvcResult result = getQueue(eventId, studentToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = responseJson(result);
        assertThat(body.has("journal")).isFalse();
        assertThat(body.get("queue").size()).isEqualTo(1);
    }

    @Test
    void staffSeesTheJournal() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Журнал"));
        joinQueueOk(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Первый");
        assertThat(advance(eventId, staffToken()).getResponse().getStatus()).isEqualTo(200);

        MvcResult result = getQueue(eventId, staffToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode journal = responseJson(result).get("journal");
        assertThat(journal).isNotNull();
        assertThat(journal.size()).isEqualTo(1);
        assertThat(journal.get(0).get("status").asText()).isEqualTo("PASSED");
        assertThat(journal.get(0).get("passedAt").isNull()).isFalse();
    }

    @Test
    void everyoneSeesTheJournalWhenVisibilityIsEveryone() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of(
                "title", "Открытый журнал", "journalVisibility", "EVERYONE"));
        joinQueueOk(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Первый");
        assertThat(advance(eventId, staffToken()).getResponse().getStatus()).isEqualTo(200);

        MvcResult result = getQueue(eventId, studentToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode journal = responseJson(result).get("journal");
        assertThat(journal).isNotNull();
        assertThat(journal.size()).isEqualTo(1);
    }

    @Test
    void repeatedRequestWithSameEtagIsNotModified() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "ETag"));
        String token = studentToken();
        joinQueueOk(eventId, token, "Первый");

        MvcResult first = getQueue(eventId, token);
        assertThat(first.getResponse().getStatus()).isEqualTo(200);
        String etag = etag(first);
        assertThat(etag).isNotBlank();

        MvcResult second = getQueue(eventId, token, etag);

        assertThat(second.getResponse().getStatus()).isEqualTo(304);
        assertThat(second.getResponse().getContentAsString()).isEmpty();
        assertThat(etag(second)).isEqualTo(etag);
    }

    @Test
    void etagChangesWhenTheQueueChanges() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "ETag"));
        String token = studentToken();
        joinQueueOk(eventId, token, "Первый");
        String before = etag(getQueue(eventId, token));

        joinQueueOk(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Второй");

        MvcResult after = getQueue(eventId, token, before);
        assertThat(after.getResponse().getStatus()).isEqualTo(200);
        assertThat(etag(after)).isNotBlank().isNotEqualTo(before);
    }

    @Test
    void stateExposesEventFieldsAndQueueOrder() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of(
                "title", "Поля", "entryLimit", 5, "entryUnit", "PERSON"));
        joinQueueOk(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Первый");

        MvcResult result = getQueue(eventId, staffToken());

        JsonNode body = responseJson(result);
        assertThat(body.get("eventId").asText()).isEqualTo(eventId.toString());
        assertThat(body.get("eventStatus").asText()).isEqualTo("OPEN");
        assertThat(body.get("entryLimit").asInt()).isEqualTo(5);
        assertThat(body.get("entryUnit").asText()).isEqualTo("PERSON");
        assertThat(body.get("updatedAt").isNull()).isFalse();
        assertThat(body.get("queue").size()).isEqualTo(1);
    }

    @Test
    void guestWithoutAccessGetsForbidden() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of(
                "title", "Только персонал", "availability", "STAFF+"));

        MvcResult result = getQueue(eventId, guestToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void unknownEventIsNotFound() throws Exception {
        MvcResult result = getQueue(UUID.randomUUID(), studentToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(errorCode(result)).isEqualTo("event_not_found");
    }
}
