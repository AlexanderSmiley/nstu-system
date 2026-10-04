package ru.nstu.system.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import ru.nstu.system.event.service.ArchiveCodec;
import ru.nstu.system.security.RoleNames;

/**
 * Tasks 9.4, 9.6 and 9.8: archiving (gzip snapshot, row deletion, outbox), the
 * hidden archived link/list, restoration and irreversible deletion (spec
 * "Архивация события", "Восстановление и безвозвратное удаление"; design.md D13,
 * D19).
 */
class EventArchiveIntegrationTest extends AbstractEventIntegrationTest {

    @Test
    void archiveCompressesRowsDeletesThemAndPublishesOnce() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Архив"));
        joinQueueOk(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Сдавший");
        joinQueueOk(eventId, guestToken(), "Гость");
        assertThat(advance(eventId, staffToken()).getResponse().getStatus()).isEqualTo(200);

        archiveClosedEvent(eventId, staffToken());

        byte[] payload = jdbcTemplate.queryForObject(
                "select archive_payload from event.event where id = ?", byte[].class, eventId);
        assertThat(payload).isNotNull();
        assertThat(payload[0] & 0xff).isEqualTo(0x1f);
        assertThat(payload[1] & 0xff).isEqualTo(0x8b);
        assertThat(new String(ArchiveCodec.gunzip(payload), StandardCharsets.UTF_8))
                .contains("Сдавший").contains("Гость");

        assertThat(jdbcTemplate.queryForObject(
                "select status from event.event where id = ?", String.class, eventId))
                .isEqualTo("ARCHIVED");
        assertThat(jdbcTemplate.queryForObject(
                "select archived_at from event.event where id = ?", java.time.Instant.class, eventId))
                .isNotNull();
        assertThat(queueRowCount(eventId)).isZero();
        // advance published entry.passed + queue.advanced, close published event.closed,
        // archive published event.archived — exactly one row per domain fact (task 9.8).
        assertThat(outboxCount()).isEqualTo(4);
        assertThat(outboxCountByType("event.archived")).isEqualTo(1);
    }

    @Test
    void archivedEventLinkAndActiveListAreHidden() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Скрытый архив"));
        String slug = jdbcTemplate.queryForObject(
                "select slug from event.event where id = ?", String.class, eventId);
        archiveClosedEvent(eventId, staffToken());

        MvcResult bySlug = mockMvc.perform(
                        authorized(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/api/events/by-slug/{slug}", slug), staffToken()))
                .andReturn();
        assertThat(bySlug.getResponse().getStatus()).isEqualTo(404);

        MvcResult list = mockMvc.perform(
                        authorized(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/api/events"), staffToken()))
                .andReturn();
        assertThat(list.getResponse().getStatus()).isEqualTo(200);
        for (JsonNode node : responseJson(list)) {
            assertThat(node.get("id").asText()).isNotEqualTo(eventId.toString());
        }
    }

    @Test
    void archivingAnOpenEventIsRejected() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Открытое"));

        MvcResult result = archiveEvent(eventId, staffToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(result)).isEqualTo("invalid_state");
    }

    @Test
    void studentCannotArchive() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Архив"));
        assertThat(closeEvent(eventId, staffToken()).getResponse().getStatus()).isEqualTo(200);

        assertThat(archiveEvent(eventId, studentToken()).getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void restoreBringsBackRowsWithTheirFields() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Восстановление"));
        UUID account = UUID.randomUUID();
        joinQueueOk(eventId, tokenFor(account, RoleNames.STUDENT), "Сдавший");
        joinQueueOk(eventId, guestToken(), "Гость");
        joinQueueOk(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Ожидающий");
        assertThat(advance(eventId, staffToken()).getResponse().getStatus()).isEqualTo(200);
        String slug = jdbcTemplate.queryForObject(
                "select slug from event.event where id = ?", String.class, eventId);
        archiveClosedEvent(eventId, staffToken());

        MvcResult restored = restoreEvent(eventId, adminToken());

        assertThat(restored.getResponse().getStatus()).isEqualTo(200);
        assertThat(responseJson(restored).get("status").asText()).isEqualTo("CLOSED");
        assertThat(jdbcTemplate.queryForObject(
                "select status from event.event where id = ?", String.class, eventId))
                .isEqualTo("CLOSED");
        assertThat(jdbcTemplate.queryForObject(
                "select archived_at from event.event where id = ?", java.time.Instant.class, eventId))
                .isNull();
        assertThat(jdbcTemplate.queryForObject(
                "select archive_payload from event.event where id = ?", byte[].class, eventId))
                .isNull();
        assertThat(queueRowCount(eventId)).isEqualTo(3);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select name, position, status, origin, holder_account_id, guest_ref, passed_at "
                        + "from event.queue_entry where event_id = ? order by position", eventId);
        Map<String, Object> passed = rowByName(rows, "Сдавший");
        assertThat(passed.get("status")).isEqualTo("PASSED");
        assertThat(passed.get("passed_at")).isNotNull();
        assertThat(passed.get("origin")).isEqualTo("JOIN");
        assertThat(passed.get("holder_account_id")).isEqualTo(account);

        Map<String, Object> guest = rowByName(rows, "Гость");
        assertThat(guest.get("status")).isEqualTo("WAITING");
        assertThat(guest.get("guest_ref")).isNotNull();
        assertThat(guest.get("holder_account_id")).isNull();

        MvcResult bySlug = mockMvc.perform(
                        authorized(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/api/events/by-slug/{slug}", slug), staffToken()))
                .andReturn();
        assertThat(bySlug.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void secondRestoreIsRejected() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Двойное восстановление"));
        archiveClosedEvent(eventId, staffToken());
        assertThat(restoreEvent(eventId, adminToken()).getResponse().getStatus()).isEqualTo(200);

        MvcResult second = restoreEvent(eventId, adminToken());

        assertThat(second.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(second)).isEqualTo("invalid_state");
    }

    @Test
    void archivedEventCanBePermanentlyDeleted() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Удаление"));
        archiveClosedEvent(eventId, staffToken());

        MvcResult deleted = deleteEvent(eventId, adminToken());

        assertThat(deleted.getResponse().getStatus()).isEqualTo(204);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from event.event where id = ?", Integer.class, eventId)).isZero();
    }

    @Test
    void nonArchivedEventCannotBeDeleted() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Закрытое"));
        assertThat(closeEvent(eventId, staffToken()).getResponse().getStatus()).isEqualTo(200);

        MvcResult result = deleteEvent(eventId, adminToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(result)).isEqualTo("invalid_state");
    }

    @Test
    void staffCannotRestoreOrDelete() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Только админ"));
        archiveClosedEvent(eventId, staffToken());

        assertThat(restoreEvent(eventId, staffToken()).getResponse().getStatus()).isEqualTo(403);
        assertThat(deleteEvent(eventId, staffToken()).getResponse().getStatus()).isEqualTo(403);
    }

    private static Map<String, Object> rowByName(List<Map<String, Object>> rows, String name) {
        return rows.stream()
                .filter(row -> name.equals(row.get("name")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Row '" + name + "' not found in " + rows));
    }
}
