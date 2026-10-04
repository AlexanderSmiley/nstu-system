package ru.nstu.system.event.service;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.nstu.system.event.domain.Event;
import ru.nstu.system.event.domain.JournalVisibility;
import ru.nstu.system.event.domain.QueueEntryRepository;
import ru.nstu.system.event.domain.QueueEntryStatus;
import ru.nstu.system.event.web.dto.QueueEntryResponse;
import ru.nstu.system.event.web.dto.QueueStateResponse;
import ru.nstu.system.security.ParsedToken;

/**
 * Read-only projection of the live queue and the surrender journal (tasks 8.8 and
 * 9.1; design.md D17, D20).
 *
 * <p>The journal rules live here in exactly one place: it is {@code null} — and
 * therefore absent from the JSON — unless the caller is staff/admin or the event
 * is configured as {@code EVERYONE}. Both {@code GET /api/events/{id}/queue} and
 * {@code GET /api/events/{id}} consume this component, so the two representations
 * can never drift apart.</p>
 */
@Component
public class QueueProjection {

    /** The two statuses that occupy a position and count towards the entry limit. */
    private static final Set<QueueEntryStatus> ACTIVE =
            Set.of(QueueEntryStatus.WAITING, QueueEntryStatus.PAUSED);

    private final QueueEntryRepository repository;

    private final EventAccessService accessService;

    public QueueProjection(QueueEntryRepository repository, EventAccessService accessService) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.accessService = Objects.requireNonNull(accessService, "accessService");
    }

    /** @return active entries ordered by ascending position */
    public List<QueueEntryResponse> activeQueue(UUID eventId) {
        return repository.findByEventIdAndStatusInOrderByPositionAsc(eventId, ACTIVE)
                .stream()
                .map(QueueEntryResponse::from)
                .toList();
    }

    /**
     * @return passed entries ordered by surrender time then id, or {@code null}
     *         when the caller may not see the journal
     */
    public List<QueueEntryResponse> journal(Event event, ParsedToken token) {
        if (!canSeeJournal(event, token)) {
            return null;
        }
        return repository
                .findByEventIdAndStatusOrderByPassedAtAscIdAsc(event.getId(), QueueEntryStatus.PASSED)
                .stream()
                .map(QueueEntryResponse::from)
                .toList();
    }

    /** @return the full queue state for the caller's role (journal may be hidden) */
    public QueueStateResponse state(Event event, ParsedToken token) {
        return new QueueStateResponse(
                event.getId(),
                event.getStatus(),
                event.getEntryLimit(),
                event.getEntryUnit(),
                event.getUpdatedAt(),
                activeQueue(event.getId()),
                journal(event, token));
    }

    private boolean canSeeJournal(Event event, ParsedToken token) {
        return accessService.isStaff(token)
                || event.getJournalVisibility() == JournalVisibility.EVERYONE;
    }
}
