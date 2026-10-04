package ru.nstu.system.event.service;

import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Read-only counts over {@code event.queue_entry} needed by the event-editing
 * rules (design.md D16, D24; task 7.5).
 *
 * <p>Only the queue group (8) owns the {@code QueueEntry} aggregate; group 7
 * needs two facts about it — whether any active entry exists and how many there
 * are — so it uses a narrow JDBC query instead of pulling the aggregate forward
 * and creating a second mapping of the same table.</p>
 */
@Component
public class QueueEntryCounter {

    private static final String COUNT_ACTIVE_SQL =
            "select count(*) from event.queue_entry where event_id = ? and status in ('WAITING', 'PAUSED')";

    private static final String COUNT_ALL_SQL =
            "select count(*) from event.queue_entry where event_id = ?";

    private final JdbcTemplate jdbcTemplate;

    public QueueEntryCounter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    /** @return number of entries that still occupy the queue ({@code WAITING}/{@code PAUSED}) */
    public long countActive(UUID eventId) {
        Objects.requireNonNull(eventId, "eventId");
        Long count = jdbcTemplate.queryForObject(COUNT_ACTIVE_SQL, Long.class, eventId);
        return count == null ? 0L : count;
    }

    /** @return {@code true} while the event's queue is non-empty */
    public boolean hasActiveEntries(UUID eventId) {
        return countActive(eventId) > 0;
    }

    /**
     * @return number of stored rows, active and passed (task 9.7 history count);
     *         zero for an archived event whose rows live in {@code archive_payload}
     */
    public long countAll(UUID eventId) {
        Objects.requireNonNull(eventId, "eventId");
        Long count = jdbcTemplate.queryForObject(COUNT_ALL_SQL, Long.class, eventId);
        return count == null ? 0L : count;
    }
}
