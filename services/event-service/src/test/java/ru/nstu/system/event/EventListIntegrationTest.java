package ru.nstu.system.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Task 7.7: the active event list, filtered by the caller role, the default group
 * and the {@code OPEN} status. Archived and closed events never appear.
 */
class EventListIntegrationTest extends AbstractEventIntegrationTest {

    @Test
    void availabilityMatrixPerRole() throws Exception {
        createEvent(staffToken(), Map.of("title", "Гостевое", "availability", "GUEST+"));
        createEvent(staffToken(), Map.of("title", "Студенческое", "availability", "STUDENT+"));
        createEvent(staffToken(), Map.of("title", "Служебное", "availability", "STAFF+"));

        assertThat(titles(guestToken())).containsExactly("Гостевое");
        assertThat(titles(studentToken())).containsExactlyInAnyOrder("Гостевое", "Студенческое");
        assertThat(titles(staffToken())).containsExactlyInAnyOrder("Гостевое", "Студенческое", "Служебное");
        assertThat(titles(adminToken())).containsExactlyInAnyOrder("Гостевое", "Студенческое", "Служебное");
    }

    @Test
    void closedAndArchivedEventsAreHidden() throws Exception {
        createEvent(staffToken(), Map.of("title", "Открытое", "availability", "GUEST+"));

        UUID closeable = createEvent(staffToken(), Map.of("title", "Закрытое", "availability", "GUEST+"));
        mockMvc.perform(authorized(post("/api/events/{id}/close", closeable), staffToken()))
                .andExpect(status().isOk());

        insertEvent("GUEST+", "ARCHIVED");

        assertThat(titles(guestToken())).containsExactly("Открытое");
        assertThat(titles(studentToken())).containsExactly("Открытое");
        assertThat(titles(staffToken())).containsExactly("Открытое");
        assertThat(titles(adminToken())).containsExactly("Открытое");
    }

    @Test
    void otherGroupsAreHidden() throws Exception {
        insertEvent(UUID.randomUUID(), "GUEST+", "OPEN");

        assertThat(titles(guestToken())).isEmpty();
        assertThat(titles(staffToken())).isEmpty();
    }

    @Test
    void emptyListReturnsOkWithEmptyArray() throws Exception {
        mockMvc.perform(authorized(get("/api/events"), guestToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void anonymousCannotListEvents() throws Exception {
        mockMvc.perform(get("/api/events"))
                .andExpect(status().isUnauthorized());
    }

    private List<String> titles(String token) throws Exception {
        MvcResult result = mockMvc.perform(authorized(get("/api/events"), token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode array = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        List<String> titles = new ArrayList<>();
        array.forEach(node -> titles.add(node.get("title").asText()));
        return titles;
    }
}
