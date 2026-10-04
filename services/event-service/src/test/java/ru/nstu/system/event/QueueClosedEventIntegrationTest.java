package ru.nstu.system.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import ru.nstu.system.security.RoleNames;

/**
 * "CLOSED is read-only" rule (customer decision, extension of task 8.x/9.x): every
 * queue mutation — pause, resume, reorder, delete — is rejected with 409
 * {@code event_closed} while the event is not {@code OPEN}, and the existing
 * {@code OPEN} behaviour is unchanged.
 */
class QueueClosedEventIntegrationTest extends AbstractEventIntegrationTest {

    @Test
    void pauseOnClosedEventIsRejected() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Закрытое"));
        UUID entryId = joinQueueOk(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Первый");
        assertThat(closeEvent(eventId, staffToken()).getResponse().getStatus()).isEqualTo(200);

        MvcResult result = pause(eventId, entryId, staffToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(result)).isEqualTo("event_closed");
        assertThat(entryStatus(eventId, entryId)).isEqualTo("WAITING");
    }

    @Test
    void resumeOnClosedEventIsRejected() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Закрытое"));
        UUID entryId = joinQueueOk(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Первый");
        assertThat(pause(eventId, entryId, staffToken()).getResponse().getStatus()).isEqualTo(200);
        assertThat(closeEvent(eventId, staffToken()).getResponse().getStatus()).isEqualTo(200);

        MvcResult result = resume(eventId, entryId, staffToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(result)).isEqualTo("event_closed");
        assertThat(entryStatus(eventId, entryId)).isEqualTo("PAUSED");
    }

    @Test
    void reorderOnClosedEventIsRejected() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Закрытое"));
        UUID first = joinQueueOk(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Первый");
        joinQueueOk(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Второй");
        assertThat(closeEvent(eventId, staffToken()).getResponse().getStatus()).isEqualTo(200);

        MvcResult result = reorder(eventId, first, 2, staffToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(result)).isEqualTo("event_closed");
        assertThat(entryPosition(eventId, first)).isEqualTo(1);
    }

    @Test
    void deleteOnClosedEventIsRejected() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Закрытое"));
        UUID entryId = joinQueueOk(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Первый");
        assertThat(closeEvent(eventId, staffToken()).getResponse().getStatus()).isEqualTo(200);

        MvcResult result = deleteEntry(eventId, entryId, staffToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(result)).isEqualTo("event_closed");
        assertThat(activeEntryCount(eventId)).isEqualTo(1);
    }

    @Test
    void openEventMutationsStillWork() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Открытое"));
        UUID first = joinQueueOk(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Первый");
        UUID second = joinQueueOk(eventId, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Второй");

        assertThat(pause(eventId, first, staffToken()).getResponse().getStatus()).isEqualTo(200);
        assertThat(resume(eventId, first, staffToken()).getResponse().getStatus()).isEqualTo(200);
        assertThat(reorder(eventId, second, 1, staffToken()).getResponse().getStatus()).isEqualTo(200);
        assertThat(deleteEntry(eventId, first, staffToken()).getResponse().getStatus()).isEqualTo(204);
        assertThat(activeEntryCount(eventId)).isEqualTo(1);
    }

    private String entryStatus(UUID eventId, UUID entryId) {
        return jdbcTemplate.queryForObject(
                "select status from event.queue_entry where id = ? and event_id = ?",
                String.class, entryId, eventId);
    }

    private int entryPosition(UUID eventId, UUID entryId) {
        Integer position = jdbcTemplate.queryForObject(
                "select position from event.queue_entry where id = ? and event_id = ?",
                Integer.class, entryId, eventId);
        return position == null ? -1 : position;
    }
}
