package ru.nstu.system.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Tasks 7.3 and 7.4: unique, well-formed slugs, the {@code by-slug} endpoint and
 * its access rules. The response must never leak queue state.
 */
class EventSlugIntegrationTest extends AbstractEventIntegrationTest {

    @Test
    void slugsAreUniqueAndWellFormedForEqualTitles() throws Exception {
        String token = staffToken();
        Set<String> slugs = new HashSet<>();

        for (int i = 0; i < 5; i++) {
            MvcResult result = createEventResult(token, Map.of("title", "Лабораторная работа"));
            assertThat(result.getResponse().getStatus()).isEqualTo(201);

            String slug = slugOf(result);
            assertThat(slug).matches("[a-z0-9-]{1,60}");
            assertThat(slugs.add(slug)).as("slug %s must be unique", slug).isTrue();
        }
        assertThat(slugs).hasSize(5);
    }

    @Test
    void bySlugReturnsAccessibleEventWithoutQueue() throws Exception {
        UUID id = createEvent(staffToken(), Map.of("title", "Открытое событие", "availability", "GUEST+"));
        String slug = slugOfEvent(id);

        mockMvc.perform(authorized(get("/api/events/by-slug/{slug}", slug), guestToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.slug").value(slug))
                .andExpect(jsonPath("$.availability").value("GUEST+"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                // Task 7.4: the by-slug projection never exposes queue or journal.
                .andExpect(jsonPath("$.queue").doesNotExist())
                .andExpect(jsonPath("$.entries").doesNotExist());
    }

    @Test
    void unknownSlugReturnsNotFound() throws Exception {
        mockMvc.perform(authorized(get("/api/events/by-slug/{slug}", "no-such-slug"), staffToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void archivedSlugReturnsNotFoundForEveryRole() throws Exception {
        UUID archivedId = insertEvent("GUEST+", "ARCHIVED");
        String slug = slugOfEvent(archivedId);

        for (String token : new String[] {staffToken(), adminToken(), studentToken(), guestToken()}) {
            mockMvc.perform(authorized(get("/api/events/by-slug/{slug}", slug), token))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void anonymousBySlugIsUnauthorized() throws Exception {
        String slug = slugOfEvent(createEvent(staffToken(), Map.of("title", "Ссылка без входа")));

        mockMvc.perform(get("/api/events/by-slug/{slug}", slug))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void staffOnlyEventIsForbiddenForStudentAndGuest() throws Exception {
        UUID id = createEvent(staffToken(), Map.of("title", "Служебное", "availability", "STAFF+"));
        String slug = slugOfEvent(id);

        mockMvc.perform(authorized(get("/api/events/by-slug/{slug}", slug), studentToken()))
                .andExpect(status().isForbidden());
        mockMvc.perform(authorized(get("/api/events/by-slug/{slug}", slug), guestToken()))
                .andExpect(status().isForbidden());
        mockMvc.perform(authorized(get("/api/events/by-slug/{slug}", slug), staffToken()))
                .andExpect(status().isOk());
        mockMvc.perform(authorized(get("/api/events/by-slug/{slug}", slug), adminToken()))
                .andExpect(status().isOk());
    }

    private String slugOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("slug").asText();
    }

    private String slugOfEvent(UUID id) {
        return jdbcTemplate.queryForObject("select slug from event.event where id = ?", String.class, id);
    }
}
