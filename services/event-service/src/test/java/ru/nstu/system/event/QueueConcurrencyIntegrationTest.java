package ru.nstu.system.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import ru.nstu.system.security.RoleNames;

/**
 * Task 8.3 / design.md D16: a pessimistic {@code SELECT ... FOR UPDATE} on the
 * event row serialises concurrent joins, so no position is duplicated and no
 * entry is lost.
 *
 * <p>The pool is sized above the number of workers because each worker holds a
 * connection while blocked on the event lock. This class contributes a dynamic
 * property, so it runs in its own context and does not affect the shared one.</p>
 */
class QueueConcurrencyIntegrationTest extends AbstractEventIntegrationTest {

    private static final int PARTICIPANTS = 10;

    @DynamicPropertySource
    static void poolSize(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "16");
    }

    @Test
    void parallelJoinsProduceUniqueContiguousPositions() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Гонка", "entryLimit", 27));

        ExecutorService executor = Executors.newFixedThreadPool(PARTICIPANTS);
        try {
            List<Callable<Integer>> tasks = new ArrayList<>();
            for (int index = 0; index < PARTICIPANTS; index++) {
                String token = tokenFor(UUID.randomUUID(), RoleNames.STUDENT);
                String name = "Участник " + index;
                tasks.add(() -> joinQueue(eventId, token, name).getResponse().getStatus());
            }
            List<Future<Integer>> results = executor.invokeAll(tasks);
            for (Future<Integer> result : results) {
                assertThat(result.get()).isEqualTo(201);
            }
        } finally {
            executor.shutdownNow();
        }

        List<Integer> positions = jdbcTemplate.queryForList(
                "select position from event.queue_entry "
                        + "where event_id = ? and status in ('WAITING','PAUSED') order by position",
                Integer.class, eventId);
        assertThat(positions)
                .hasSize(PARTICIPANTS)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from event.queue_entry where event_id = ?", Integer.class, eventId))
                .isEqualTo(PARTICIPANTS);
        assertThat(jdbcTemplate.queryForObject(
                "select count(distinct name_normalized) from event.queue_entry where event_id = ?",
                Integer.class, eventId))
                .isEqualTo(PARTICIPANTS);
    }
}
