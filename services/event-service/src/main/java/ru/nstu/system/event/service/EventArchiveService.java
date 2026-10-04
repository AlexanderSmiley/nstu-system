package ru.nstu.system.event.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.nstu.system.contracts.events.DomainEvent;
import ru.nstu.system.contracts.events.EventArchivedPayload;
import ru.nstu.system.contracts.events.EventJson;
import ru.nstu.system.contracts.events.EventTypes;
import ru.nstu.system.contracts.outbox.OutboxWriter;
import ru.nstu.system.event.domain.Event;
import ru.nstu.system.event.domain.EventRepository;
import ru.nstu.system.event.domain.EventStatus;
import ru.nstu.system.event.domain.QueueEntry;
import ru.nstu.system.event.domain.QueueEntryRepository;
import ru.nstu.system.event.error.ApiException;
import ru.nstu.system.security.ParsedToken;

/**
 * Event archival lifecycle: manual and scheduled archiving, restoration and
 * irreversible deletion (tasks 9.4-9.6; design.md D13, D19; spec "Архивация
 * события", "Автоматическая архивация по сроку хранения", "Восстановление и
 * безвозвратное удаление").
 *
 * <p>Archiving snapshots every {@code queue_entry} row of a {@code CLOSED} event
 * to JSON, gzips it into {@code event.archive_payload}, deletes the live rows,
 * flips the event to {@code ARCHIVED} and writes {@code event.archived} to the
 * outbox — all in one transaction, so the domain change and the event are
 * committed atomically (design.md D13). Restoring reverses the snapshot; the
 * permanent delete is administrator-only and allowed only for {@code ARCHIVED}
 * events.</p>
 *
 * <p>The retention sweep uses {@code FOR UPDATE SKIP LOCKED} so two service
 * instances never archive the same event; {@code ARCHIVED} rows are excluded by
 * the query, hence never reprocessed (task 9.5).</p>
 */
@Service
public class EventArchiveService {

    private static final Logger log = LoggerFactory.getLogger(EventArchiveService.class);

    /** Maximum number of due events claimed per sweep. */
    static final int SWEEP_BATCH = 100;

    private final EventRepository eventRepository;

    private final QueueEntryRepository queueEntryRepository;

    private final OutboxWriter outboxWriter;

    private final EventAccessService accessService;

    private final ObjectMapper eventMapper = EventJson.objectMapper();

    public EventArchiveService(EventRepository eventRepository,
                              QueueEntryRepository queueEntryRepository,
                              OutboxWriter outboxWriter,
                              EventAccessService accessService) {
        this.eventRepository = Objects.requireNonNull(eventRepository, "eventRepository");
        this.queueEntryRepository = Objects.requireNonNull(queueEntryRepository, "queueEntryRepository");
        this.outboxWriter = Objects.requireNonNull(outboxWriter, "outboxWriter");
        this.accessService = Objects.requireNonNull(accessService, "accessService");
    }

    // ------------------------------------------------------------------
    // 9.4 — manual archival
    // ------------------------------------------------------------------

    /**
     * Archives a {@code CLOSED} event (staff or administrator).
     *
     * @throws ApiException 404 when the event does not exist, 409 {@code invalid_state}
     *                      when it is not {@code CLOSED}, 403 for other roles
     */
    @Transactional
    public Event archive(UUID eventId, ParsedToken token) {
        requireStaff(token);
        Objects.requireNonNull(eventId, "eventId");
        Event event = eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> ApiException.notFound("event_not_found", "Событие не найдено"));
        if (!accessService.canView(token, event.getAvailability())) {
            throw ApiException.forbidden("event_forbidden", "Недостаточно прав для доступа к событию");
        }
        return archiveLocked(event);
    }

    // ------------------------------------------------------------------
    // 9.5 — retention sweep
    // ------------------------------------------------------------------

    /**
     * Archives every {@code CLOSED} event whose retention window has elapsed.
     *
     * <p>Public so the integration tests can trigger the sweep deterministically;
     * the {@link Scheduled} annotation also runs it on a fixed delay read from
     * {@code nstu.archive.sweep-interval} (ISO-8601, default {@code PT10M}). The
     * method is transactional, which is required for the {@code FOR UPDATE SKIP
     * LOCKED} claim and the atomic archive of each claimed event.</p>
     *
     * @return number of events archived by this run
     */
    @Scheduled(fixedDelayString = "${nstu.archive.sweep-interval:PT10M}")
    @Transactional
    public int sweepOnce() {
        List<Event> due = eventRepository.findDueForArchiveForUpdateSkipLocked(SWEEP_BATCH);
        int archived = 0;
        for (Event event : due) {
            if (event.getStatus() != EventStatus.CLOSED) {
                continue;
            }
            archiveLocked(event);
            archived++;
        }
        if (archived > 0) {
            log.info("Retention sweep archived {} event(s)", archived);
        }
        return archived;
    }

    // ------------------------------------------------------------------
    // 9.6 — restore / permanent delete
    // ------------------------------------------------------------------

    /** Restores an archived event and its rows; administrator only. */
    @Transactional
    public Event restore(UUID eventId, ParsedToken token) {
        requireAdmin(token);
        Objects.requireNonNull(eventId, "eventId");
        Event event = eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> ApiException.notFound("event_not_found", "Событие не найдено"));
        if (event.getStatus() != EventStatus.ARCHIVED) {
            throw ApiException.conflict("invalid_state", "Восстановить можно только архивное событие");
        }
        byte[] payload = event.getArchivePayload();
        if (payload == null) {
            throw ApiException.conflict("invalid_state", "Архивное событие не содержит данных");
        }
        List<ArchivedEntry> snapshot = deserialize(payload);
        List<QueueEntry> restored = snapshot.stream()
                .map(entry -> entry.toEntity(eventId))
                .toList();
        queueEntryRepository.saveAll(restored);
        event.restore();
        log.info("Restored event {} from archive ({} entries)", eventId, restored.size());
        return event;
    }

    /** Permanently deletes an archived event; administrator only. */
    @Transactional
    public void delete(UUID eventId, ParsedToken token) {
        requireAdmin(token);
        Objects.requireNonNull(eventId, "eventId");
        Event event = eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> ApiException.notFound("event_not_found", "Событие не найдено"));
        if (event.getStatus() != EventStatus.ARCHIVED) {
            throw ApiException.conflict("invalid_state",
                    "Безвозвратно удалить можно только архивное событие");
        }
        queueEntryRepository.deleteByEventId(eventId);
        eventRepository.delete(event);
        log.info("Permanently deleted archived event {} by {}", eventId, token.subject());
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    /**
     * Archives an already management-locked event. The order is significant: the
     * snapshot is written to the event, the live rows are deleted, and only then
     * is {@code event.archived} appended to the outbox — all in the caller's
     * transaction.
     */
    private Event archiveLocked(Event event) {
        if (event.getStatus() != EventStatus.CLOSED) {
            throw ApiException.conflict("invalid_state", "Архивировать можно только закрытое событие");
        }
        List<QueueEntry> entries = queueEntryRepository.findByEventIdOrderByPositionAsc(event.getId());
        byte[] payload = ArchiveCodec.gzip(serialize(entries));
        event.archive(payload, Instant.now());
        queueEntryRepository.deleteByEventId(event.getId());
        outboxWriter.write(DomainEvent.of(
                EventTypes.EVENT_ARCHIVED, new EventArchivedPayload(event.getId())));
        log.info("Archived event {}: {} entries, {} bytes compressed",
                event.getId(), entries.size(), payload.length);
        return event;
    }

    private byte[] serialize(List<QueueEntry> entries) {
        List<ArchivedEntry> snapshot = entries.stream().map(ArchivedEntry::from).toList();
        try {
            return eventMapper.writeValueAsBytes(snapshot);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("cannot serialise archive payload", ex);
        }
    }

    private List<ArchivedEntry> deserialize(byte[] payload) {
        byte[] json = ArchiveCodec.gunzip(payload);
        try {
            return eventMapper.readValue(json, new TypeReference<List<ArchivedEntry>>() {
            });
        } catch (IOException ex) {
            throw new IllegalStateException("cannot deserialise archive payload", ex);
        }
    }

    private void requireStaff(ParsedToken token) {
        if (!accessService.isStaff(token)) {
            throw ApiException.forbidden("event_forbidden", "Недостаточно прав для архивации события");
        }
    }

    private void requireAdmin(ParsedToken token) {
        if (!accessService.isAdmin(token)) {
            throw ApiException.forbidden("event_forbidden",
                    "Действие доступно только администратору");
        }
    }
}
