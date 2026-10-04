package ru.nstu.system.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Task 9.7: role-scoped event history (spec "Восстановление и безвозвратное
 * удаление", scenario "Состав истории для разных ролей"). Staff see {@code CLOSED}
 * only, administrators also see {@code ARCHIVED}, students and guests get an empty
 * list; archived events never appear in the active list.
 */
class EventHistoryIntegrationTest extends AbstractEventIntegrationTest {

    @Test
    void staffSeesOnlyClosedAndAdminSeesArchivedToo() throws Exception {
        createEvent(staffToken(), Map.of("title", "Открытое"));
        UUID closed = createEvent(staffToken(), Map.of("title", "Закрытое"));
        assertThat(closeEvent(closed, staffToken()).getResponse().getStatus()).isEqualTo(200);
        UUID archived = createEvent(staffToken(), Map.of("title", "Архивное"));
        archiveClosedEvent(archived, staffToken());

        MvcResult staffHistory = getHistory(staffToken());

        assertThat(staffHistory.getResponse().getStatus()).isEqualTo(200);
        assertThat(eventIds(responseJson(staffHistory))).containsExactly(closed);

        MvcResult adminHistory = getHistory(adminToken());

        assertThat(adminHistory.getResponse().getStatus()).isEqualTo(200);
        assertThat(eventIds(responseJson(adminHistory)))
                .containsExactlyInAnyOrder(closed, archived);
    }

    @Test
    void historyExposesStatusAndTimestamps() throws Exception {
        UUID closed = createEvent(staffToken(), Map.of("title", "Закрытое"));
        assertThat(closeEvent(closed, staffToken()).getResponse().getStatus()).isEqualTo(200);

        JsonNode entry = responseJson(getHistory(staffToken())).get(0);

        assertThat(entry.get("status").asText()).isEqualTo("CLOSED");
        assertThat(entry.get("title").asText()).isEqualTo("Закрытое");
        assertThat(entry.get("closedAt").isNull()).isFalse();
        assertThat(entry.get("retentionDays").asInt()).isEqualTo(14);
        assertThat(entry.has("entryCount")).isTrue();
    }

    @Test
    void studentAndGuestSeeEmptyHistory() throws Exception {
        UUID closed = createEvent(staffToken(), Map.of("title", "Закрытое"));
        assertThat(closeEvent(closed, staffToken()).getResponse().getStatus()).isEqualTo(200);

        for (String token : new String[] {studentToken(), guestToken()}) {
            MvcResult result = getHistory(token);
            assertThat(result.getResponse().getStatus()).isEqualTo(200);
            assertThat(responseJson(result).size()).isZero();
        }
    }

    @Test
    void archivedEventIsAbsentFromTheActiveList() throws Exception {
        UUID archived = createEvent(staffToken(), Map.of("title", "Архивное"));
        archiveClosedEvent(archived, staffToken());

        MvcResult list = mockMvc.perform(authorized(get("/api/events"), staffToken())).andReturn();

        assertThat(list.getResponse().getStatus()).isEqualTo(200);
        assertThat(eventIds(responseJson(list))).doesNotContain(archived);
    }

    private static Set<UUID> eventIds(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false)
                .map(node -> UUID.fromString(node.get("id").asText()))
                .collect(Collectors.toSet());
    }
}
