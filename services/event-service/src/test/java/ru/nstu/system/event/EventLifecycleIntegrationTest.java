package ru.nstu.system.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Task 7.6: opening and closing an event.
 *
 * <p>Closing writes {@code event.closed} to the outbox in the same transaction;
 * opening publishes nothing. A second close of an already closed event is a
 * no-op (200, identical {@code closed_at}, no second outbox row): the operation
 * is naturally idempotent, a client retry after a lost response must not emit a
 * duplicate notification, and repeating it cannot violate any invariant.</p>
 */
class EventLifecycleIntegrationTest extends AbstractEventIntegrationTest {

    @Test
    void closeSetsStatusClosedAtAndWritesExactlyOneOutboxEvent() throws Exception {
        UUID id = createEvent(staffToken(), Map.of("title", "Закрываемое событие"));
        String slug = jdbcTemplate.queryForObject(
                "select slug from event.event where id = ?", String.class, id);

        mockMvc.perform(authorized(post("/api/events/{id}/close", id), staffToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        assertThat(jdbcTemplate.queryForObject(
                "select status from event.event where id = ?", String.class, id)).isEqualTo("CLOSED");
        assertThat(jdbcTemplate.queryForObject(
                "select closed_at from event.event where id = ?", Instant.class, id)).isNotNull();

        assertThat(outboxCount()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select event_type from event.outbox", String.class)).isEqualTo("event.closed");
        String payload = jdbcTemplate.queryForObject("select payload::text from event.outbox", String.class);
        assertThat(payload).contains("event.closed").contains(slug);
    }

    @Test
    void openClearsClosedAtAndPublishesNothing() throws Exception {
        UUID id = createEvent(staffToken(), Map.of("title", "Переоткрываемое"));

        mockMvc.perform(authorized(post("/api/events/{id}/close", id), staffToken()))
                .andExpect(status().isOk());
        int outboxAfterClose = outboxCount();

        mockMvc.perform(authorized(post("/api/events/{id}/open", id), staffToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));

        assertThat(jdbcTemplate.queryForObject(
                "select status from event.event where id = ?", String.class, id)).isEqualTo("OPEN");
        assertThat(jdbcTemplate.queryForObject(
                "select closed_at from event.event where id = ?", Instant.class, id)).isNull();
        assertThat(outboxCount()).isEqualTo(outboxAfterClose);
    }

    @Test
    void closingAlreadyClosedEventIsIdempotent() throws Exception {
        UUID id = createEvent(staffToken(), Map.of("title", "Двойное закрытие"));

        mockMvc.perform(authorized(post("/api/events/{id}/close", id), staffToken()))
                .andExpect(status().isOk());
        Instant firstClosedAt = jdbcTemplate.queryForObject(
                "select closed_at from event.event where id = ?", Instant.class, id);

        mockMvc.perform(authorized(post("/api/events/{id}/close", id), staffToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        assertThat(outboxCount()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select closed_at from event.event where id = ?", Instant.class, id)).isEqualTo(firstClosedAt);
    }

    @Test
    void adminCanCloseAndOpen() throws Exception {
        UUID id = createEvent(adminToken(), Map.of("title", "Админский цикл"));

        mockMvc.perform(authorized(post("/api/events/{id}/close", id), adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
        mockMvc.perform(authorized(post("/api/events/{id}/open", id), adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void studentAndGuestCannotCloseOrOpen() throws Exception {
        UUID id = createEvent(staffToken(), Map.of("title", "Только персонал"));

        for (String token : new String[] {studentToken(), guestToken()}) {
            mockMvc.perform(authorized(post("/api/events/{id}/close", id), token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(authorized(post("/api/events/{id}/open", id), token))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void closeUnknownEventReturnsNotFound() throws Exception {
        mockMvc.perform(authorized(post("/api/events/{id}/close", UUID.randomUUID()), staffToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void archivedEventCannotBeClosedOrOpened() throws Exception {
        UUID id = insertEvent("GUEST+", "ARCHIVED");

        mockMvc.perform(authorized(post("/api/events/{id}/close", id), staffToken()))
                .andExpect(status().isConflict());
        mockMvc.perform(authorized(post("/api/events/{id}/open", id), staffToken()))
                .andExpect(status().isConflict());
    }
}
