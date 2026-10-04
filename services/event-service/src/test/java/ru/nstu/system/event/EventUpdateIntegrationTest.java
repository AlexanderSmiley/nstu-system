package ru.nstu.system.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Task 7.5: the event-editing matrix — what staff may change, what is
 * administrator-only, when the entry unit is locked, and that lowering the limit
 * never deletes entries.
 *
 * <p>State conflicts (entry unit locked while the queue is non-empty, archived
 * events) are answered with 409: the request is well-formed but the current
 * resource state forbids it, which is exactly what 409 Conflict expresses and is
 * distinguishable by the SPA from a malformed request.</p>
 */
class EventUpdateIntegrationTest extends AbstractEventIntegrationTest {

    @Test
    void staffUpdatesAllEditableFields() throws Exception {
        UUID id = createEvent(staffToken(), Map.of("title", "Старое"));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "Новое название");
        body.put("description", "Новое описание");
        body.put("availability", "STUDENT+");
        body.put("startsAt", "2026-11-01T10:00:00Z");
        body.put("entryLimit", 10);
        body.put("journalVisibility", "EVERYONE");

        MvcResult result = patchEvent(staffToken(), id, body);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        assertThat(jdbcTemplate.queryForObject(
                "select title from event.event where id = ?", String.class, id)).isEqualTo("Новое название");
        assertThat(jdbcTemplate.queryForObject(
                "select description from event.event where id = ?", String.class, id)).isEqualTo("Новое описание");
        assertThat(jdbcTemplate.queryForObject(
                "select availability from event.event where id = ?", String.class, id)).isEqualTo("STUDENT+");
        assertThat(jdbcTemplate.queryForObject(
                "select starts_at from event.event where id = ?", Instant.class, id))
                .isEqualTo(Instant.parse("2026-11-01T10:00:00Z"));
        assertThat(jdbcTemplate.queryForObject(
                "select entry_limit from event.event where id = ?", Integer.class, id)).isEqualTo(10);
        assertThat(jdbcTemplate.queryForObject(
                "select journal_visibility from event.event where id = ?", String.class, id)).isEqualTo("EVERYONE");
    }

    @Test
    void staffCannotChangeSlugOrRetentionDays() throws Exception {
        UUID id = createEvent(staffToken(), Map.of("title", "Персонал"));

        assertThat(patchEvent(staffToken(), id, Map.of("slug", "staff-new-slug")).getResponse().getStatus())
                .isEqualTo(403);
        assertThat(patchEvent(staffToken(), id, Map.of("retentionDays", 20)).getResponse().getStatus())
                .isEqualTo(403);
    }

    @Test
    void adminCanChangeSlugAndRetentionDaysAndNewSlugResolves() throws Exception {
        UUID id = createEvent(adminToken(), Map.of("title", "Админ"));

        MvcResult result = patchEvent(adminToken(), id, Map.of("slug", "admin-new-slug", "retentionDays", 45));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        assertThat(jdbcTemplate.queryForObject(
                "select slug from event.event where id = ?", String.class, id)).isEqualTo("admin-new-slug");
        assertThat(jdbcTemplate.queryForObject(
                "select retention_days from event.event where id = ?", Integer.class, id)).isEqualTo(45);

        mockMvc.perform(authorized(get("/api/events/by-slug/{slug}", "admin-new-slug"), adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void entryUnitChangeIsRejectedWhileQueueIsNotEmpty() throws Exception {
        UUID id = createEvent(staffToken(), Map.of("title", "Очередь не пуста"));
        insertActiveEntry(id, "Иванов", 1, "WAITING");

        MvcResult result = patchEvent(staffToken(), id, Map.of("entryUnit", "PERSON"));
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(jdbcTemplate.queryForObject(
                "select entry_unit from event.event where id = ?", String.class, id)).isEqualTo("BRIGADE");
    }

    @Test
    void entryUnitChangeIsAllowedWhenQueueIsEmpty() throws Exception {
        UUID id = createEvent(staffToken(), Map.of("title", "Очередь пуста"));

        MvcResult result = patchEvent(staffToken(), id, Map.of("entryUnit", "PERSON"));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(jdbcTemplate.queryForObject(
                "select entry_unit from event.event where id = ?", String.class, id)).isEqualTo("PERSON");
    }

    @Test
    void loweringEntryLimitKeepsActiveEntries() throws Exception {
        UUID id = createEvent(staffToken(), Map.of("title", "Много записей"));
        insertActiveEntry(id, "Иванов", 1, "WAITING");
        insertActiveEntry(id, "Петров", 2, "WAITING");
        insertActiveEntry(id, "Сидоров", 3, "PAUSED");

        MvcResult result = patchEvent(staffToken(), id, Map.of("entryLimit", 1));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        assertThat(jdbcTemplate.queryForObject(
                "select entry_limit from event.event where id = ?", Integer.class, id)).isEqualTo(1);
        assertThat(activeEntryCount(id)).isEqualTo(3);
    }

    @Test
    void studentAndGuestCannotUpdate() throws Exception {
        UUID id = createEvent(staffToken(), Map.of("title", "Только персонал"));

        assertThat(patchEvent(studentToken(), id, Map.of("title", "Студент")).getResponse().getStatus())
                .isEqualTo(403);
        assertThat(patchEvent(guestToken(), id, Map.of("title", "Гость")).getResponse().getStatus())
                .isEqualTo(403);
    }

    @Test
    void updateUnknownEventReturnsNotFound() throws Exception {
        MvcResult result = patchEvent(staffToken(), UUID.randomUUID(), Map.of("title", "Нет такого"));
        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void archivedEventCannotBeUpdated() throws Exception {
        UUID id = insertEvent("GUEST+", "ARCHIVED");

        MvcResult result = patchEvent(staffToken(), id, Map.of("title", "Поздно"));
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
    }

    private MvcResult patchEvent(String token, UUID id, Map<String, Object> body) throws Exception {
        return mockMvc.perform(authorized(patch("/api/events/{id}", id), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andReturn();
    }
}
