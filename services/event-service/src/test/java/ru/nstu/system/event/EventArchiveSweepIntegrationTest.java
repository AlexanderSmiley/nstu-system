package ru.nstu.system.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.nstu.system.contracts.Groups;

/**
 * Task 9.5: automatic archiving by retention window (spec "Автоматическая
 * архивация по сроку хранения"; design.md D19). The sweep is triggered
 * deterministically via {@code archiveService.sweepOnce()}; the {@code
 * nstu.archive.sweep-interval} is pushed to 24 hours in the shared test setup.
 */
class EventArchiveSweepIntegrationTest extends AbstractEventIntegrationTest {

    @Test
    void expiredClosedEventIsArchivedByTheSweep() throws Exception {
        UUID eventId = insertClosedEvent(1, "now() - interval '2 days'");

        int archived = archiveService.sweepOnce();

        assertThat(archived).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select status from event.event where id = ?", String.class, eventId))
                .isEqualTo("ARCHIVED");
        assertThat(jdbcTemplate.queryForObject(
                "select archived_at from event.event where id = ?", Instant.class, eventId))
                .isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "select archive_payload is not null from event.event where id = ?", Boolean.class, eventId))
                .isTrue();
        assertThat(outboxCountByType("event.archived")).isEqualTo(1);
    }

    @Test
    void alreadyArchivedEventIsNotProcessedAgain() throws Exception {
        insertClosedEvent(1, "now() - interval '2 days'");
        assertThat(archiveService.sweepOnce()).isEqualTo(1);

        int secondRun = archiveService.sweepOnce();

        assertThat(secondRun).isZero();
        assertThat(outboxCountByType("event.archived")).isEqualTo(1);
    }

    @Test
    void openAndFreshEventsAreUntouched() throws Exception {
        UUID open = createEvent(staffToken(), Map.of("title", "Открытое"));
        UUID fresh = insertClosedEvent(14, "now()");

        int archived = archiveService.sweepOnce();

        assertThat(archived).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select status from event.event where id = ?", String.class, open))
                .isEqualTo("OPEN");
        assertThat(jdbcTemplate.queryForObject(
                "select status from event.event where id = ?", String.class, fresh))
                .isEqualTo("CLOSED");
        assertThat(outboxCountByType("event.archived")).isZero();
    }

    private UUID insertClosedEvent(int retentionDays, String closedAtExpression) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into event.event (id, group_id, title, type, availability, slug, status, "
                        + "retention_days, closed_at, created_by) "
                        + "values (?, ?, ?, 'QUEUE', 'GUEST+', ?, 'CLOSED', ?, "
                        + closedAtExpression + ", ?)",
                id, Groups.DEFAULT_GROUP_ID, "Просроченное событие", "sweep-" + id,
                retentionDays, UUID.randomUUID());
        return id;
    }
}
