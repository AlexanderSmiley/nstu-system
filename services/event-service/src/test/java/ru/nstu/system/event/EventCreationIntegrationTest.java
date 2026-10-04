package ru.nstu.system.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import ru.nstu.system.contracts.Groups;
import ru.nstu.system.security.RoleNames;

/**
 * Tasks 7.1 and 7.2: event creation with defaults, type/title validation, role
 * restrictions and the retention-days matrix.
 */
class EventCreationIntegrationTest extends AbstractEventIntegrationTest {

    @Test
    void createWithTitleOnlyAppliesDefaults() throws Exception {
        UUID staffId = UUID.randomUUID();

        MvcResult result = createEventResult(staffToken(staffId), Map.of("title", "Лабораторная работа"));
        assertThat(result.getResponse().getStatus()).isEqualTo(201);

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(body.get("availability").asText()).isEqualTo("GUEST+");
        assertThat(body.get("entryLimit").asInt()).isEqualTo(27);
        assertThat(body.get("entryUnit").asText()).isEqualTo("BRIGADE");
        assertThat(body.get("journalVisibility").asText()).isEqualTo("STAFF");
        assertThat(body.get("retentionDays").asInt()).isEqualTo(14);
        assertThat(body.get("status").asText()).isEqualTo("OPEN");
        assertThat(body.get("groupId").asText()).isEqualTo(Groups.DEFAULT_GROUP_ID.toString());
        assertThat(body.get("startsAt").isNull()).isTrue();
        assertThat(body.get("slug").asText()).isNotBlank();

        UUID id = UUID.fromString(body.get("id").asText());
        assertThat(jdbcTemplate.queryForObject(
                "select created_by from event.event where id = ?", UUID.class, id)).isEqualTo(staffId);
        assertThat(jdbcTemplate.queryForObject(
                "select type from event.event where id = ?", String.class, id)).isEqualTo("QUEUE");
    }

    @Test
    void createWithBlankTitleIsRejected() throws Exception {
        mockMvc.perform(authorized(post("/api/events"), staffToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "   "))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWithMissingTitleIsRejected() throws Exception {
        MvcResult result = createEventResult(staffToken(), Map.of("description", "нет названия"));
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void createWithNonQueueTypeIsRejected() throws Exception {
        MvcResult result = createEventResult(staffToken(), Map.of("title", "Встреча", "type", "MEETING"));
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void createWithQueueTypeIsAccepted() throws Exception {
        MvcResult result = createEventResult(staffToken(), Map.of("title", "Очередь", "type", "QUEUE"));
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void studentCannotCreate() throws Exception {
        MvcResult result = createEventResult(studentToken(), Map.of("title", "Нельзя"));
        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void guestCannotCreate() throws Exception {
        MvcResult result = createEventResult(guestToken(), Map.of("title", "Нельзя"));
        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void adminCanCreate() throws Exception {
        MvcResult result = createEventResult(adminToken(), Map.of("title", "Админское событие"));
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void createPersistsCustomValues() throws Exception {
        UUID id = createEvent(staffToken(), Map.of(
                "title", "Зачёт",
                "description", "Описание",
                "availability", "STUDENT+",
                "startsAt", "2026-10-01T12:00:00Z",
                "entryLimit", 5,
                "entryUnit", "PERSON",
                "journalVisibility", "EVERYONE",
                "retentionDays", 20));

        assertThat(jdbcTemplate.queryForObject(
                "select availability from event.event where id = ?", String.class, id)).isEqualTo("STUDENT+");
        assertThat(jdbcTemplate.queryForObject(
                "select entry_limit from event.event where id = ?", Integer.class, id)).isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject(
                "select entry_unit from event.event where id = ?", String.class, id)).isEqualTo("PERSON");
        assertThat(jdbcTemplate.queryForObject(
                "select journal_visibility from event.event where id = ?", String.class, id)).isEqualTo("EVERYONE");
        assertThat(jdbcTemplate.queryForObject(
                "select retention_days from event.event where id = ?", Integer.class, id)).isEqualTo(20);
        assertThat(jdbcTemplate.queryForObject(
                "select starts_at from event.event where id = ?", Instant.class, id))
                .isEqualTo(Instant.parse("2026-10-01T12:00:00Z"));
    }

    @ParameterizedTest(name = "{0} retentionDays={1} -> {2}")
    @CsvSource({
            "STAFF, 0, 400",
            "STAFF, -1, 400",
            "ADMIN, 0, 400",
            "ADMIN, -1, 400",
            "STAFF, 1, 201",
            "ADMIN, 1, 201",
            "STAFF, 30, 201",
            "ADMIN, 30, 201",
            "STAFF, 31, 400",
            "ADMIN, 31, 201",
            "STAFF, 365, 400",
            "ADMIN, 365, 201"
    })
    void retentionDaysMatrix(String role, int retentionDays, int expectedStatus) throws Exception {
        MvcResult result = createEventResult(
                tokenForRole(role),
                Map.of("title", "Срок хранения", "retentionDays", retentionDays));

        assertThat(result.getResponse().getStatus()).isEqualTo(expectedStatus);
        if (expectedStatus == 201) {
            UUID id = eventId(result);
            assertThat(jdbcTemplate.queryForObject(
                    "select retention_days from event.event where id = ?", Integer.class, id))
                    .isEqualTo(retentionDays);
        }
    }

    @ParameterizedTest(name = "entryLimit={0} -> {1}")
    @CsvSource({
            "1, 201",
            "1000, 201",
            "27, 201",
            "0, 400",
            "-1, 400",
            "1001, 400",
            "100000, 400"
    })
    void entryLimitBounds(int entryLimit, int expectedStatus) throws Exception {
        MvcResult result = createEventResult(
                staffToken(),
                Map.of("title", "Лимит записей", "entryLimit", entryLimit));

        assertThat(result.getResponse().getStatus()).isEqualTo(expectedStatus);
        if (expectedStatus == 201) {
            UUID id = eventId(result);
            assertThat(jdbcTemplate.queryForObject(
                    "select entry_limit from event.event where id = ?", Integer.class, id))
                    .isEqualTo(entryLimit);
        } else {
            assertThat(errorCode(result)).isEqualTo("invalid_entry_limit");
        }
    }

    private String tokenForRole(String role) {
        return switch (role) {
            case RoleNames.STAFF -> staffToken();
            case RoleNames.ADMIN -> adminToken();
            default -> throw new IllegalArgumentException("unsupported role " + role);
        };
    }
}
