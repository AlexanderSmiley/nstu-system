package ru.nstu.system.event.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.nstu.system.contracts.events.DomainEvent;
import ru.nstu.system.contracts.events.EntryPassedPayload;
import ru.nstu.system.contracts.events.EventTypes;
import ru.nstu.system.contracts.events.QueueAdvancedPayload;
import ru.nstu.system.contracts.outbox.OutboxWriter;
import ru.nstu.system.event.domain.Event;
import ru.nstu.system.event.domain.EventRepository;
import ru.nstu.system.event.domain.EventStatus;
import ru.nstu.system.event.domain.QueueEntry;
import ru.nstu.system.event.domain.QueueEntryRepository;
import ru.nstu.system.event.domain.QueueEntryStatus;
import ru.nstu.system.event.error.ApiException;
import ru.nstu.system.event.web.dto.CarryOverRequest;
import ru.nstu.system.event.web.dto.CarryOverResponse;
import ru.nstu.system.event.web.dto.QueueStateResponse;
import ru.nstu.system.security.ParsedToken;
import ru.nstu.system.security.RoleNames;

/**
 * Queue operations for a single event (tasks 8.1-8.8 and 9.2-9.3; design.md D10,
 * D12, D15, D16, D17, D18, D20; spec "Очередь сдачи").
 *
 * <p>Every mutating method runs in one transaction and starts by taking a
 * pessimistic lock on the owning event row
 * ({@link EventRepository#findByIdForUpdate(UUID)}), which serialises concurrent
 * queue changes for an event (design.md D16). Authorisation is enforced here, not
 * in the security filter chain, because it depends on the event's availability
 * and on the caller role (design.md D10).</p>
 *
 * <p>"Next" writes {@code entry.passed} and {@code queue.advanced} to the outbox in
 * the same transaction as the status change (design.md D13) — the outbox row is
 * committed together with the domain change or not at all.</p>
 */
@Service
public class QueueService {

    private static final Logger log = LoggerFactory.getLogger(QueueService.class);

    /** The two statuses that occupy a position and count towards the entry limit. */
    private static final Set<QueueEntryStatus> ACTIVE =
            Set.of(QueueEntryStatus.WAITING, QueueEntryStatus.PAUSED);

    /** Prefix of a guest token subject, {@code guest:<uuid>} (design.md D11). */
    private static final String GUEST_SUBJECT_PREFIX = "guest:";

    /**
     * Temporary position offset used while renumbering. All active positions are
     * moved above any real {@code 1..K} value first, so stamping the final values
     * cannot transiently violate the partial unique index.
     */
    private static final int REORDER_OFFSET = 1_000_000;

    private final EventRepository eventRepository;

    private final QueueEntryRepository repository;

    private final EventAccessService accessService;

    private final StudentProfileClient profileClient;

    private final OutboxWriter outboxWriter;

    private final QueueProjection projection;

    public QueueService(EventRepository eventRepository,
                        QueueEntryRepository repository,
                        EventAccessService accessService,
                        StudentProfileClient profileClient,
                        OutboxWriter outboxWriter,
                        QueueProjection projection) {
        this.eventRepository = Objects.requireNonNull(eventRepository, "eventRepository");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.accessService = Objects.requireNonNull(accessService, "accessService");
        this.profileClient = Objects.requireNonNull(profileClient, "profileClient");
        this.outboxWriter = Objects.requireNonNull(outboxWriter, "outboxWriter");
        this.projection = Objects.requireNonNull(projection, "projection");
    }

    // ------------------------------------------------------------------
    // 8.1 / 8.2 — joining
    // ------------------------------------------------------------------

    /**
     * Puts the caller at the end of the queue.
     *
     * <p>Guests must supply a non-blank name (400 {@code invalid_name} otherwise);
     * accounts may omit it, in which case the name is fetched from
     * {@code student-service} (400 {@code profile_required} / 503
     * {@code profile_unavailable}). The name must be free among active entries
     * (409 {@code name_taken}), the participant must not already hold an active
     * entry (409 {@code already_joined}), the event must be {@code OPEN} (409
     * {@code event_closed}) and below its limit (409 {@code queue_full}).</p>
     */
    @Transactional
    public QueueEntry join(UUID eventId, ParsedToken token, String requestedName) {
        Event event = lockEvent(eventId);
        requireAccess(token, event);
        requireJoinable(event);

        boolean guest = isGuest(token);
        UUID accountId = guest ? null : requireAccountId(token);
        UUID guestRef = guest ? guestRefOf(token) : null;

        boolean alreadyJoined = guest
                ? repository.existsByEventIdAndGuestRefAndStatusIn(eventId, guestRef, ACTIVE)
                : repository.existsByEventIdAndHolderAccountIdAndStatusIn(eventId, accountId, ACTIVE);
        if (alreadyJoined) {
            throw ApiException.conflict("already_joined",
                    "У вас уже есть активная запись в этом событии");
        }

        String displayName = guest
                ? requireGuestName(requestedName)
                : resolveAccountName(accountId, requestedName);
        validateNameLength(displayName);
        String normalizedName = QueueNameNormalizer.normalize(displayName);

        if (repository.existsByEventIdAndNameNormalizedAndStatusIn(eventId, normalizedName, ACTIVE)) {
            throw ApiException.conflict("name_taken", "Это имя уже занято в очереди");
        }
        if (repository.countByEventIdAndStatusIn(eventId, ACTIVE) >= event.getEntryLimit()) {
            throw ApiException.conflict("queue_full", "Очередь заполнена");
        }

        int position = repository.findFirstByEventIdAndStatusInOrderByPositionDesc(eventId, ACTIVE)
                .map(QueueEntry::getPosition)
                .orElse(0) + 1;

        QueueEntry entry = QueueEntry.join(
                UUID.randomUUID(), eventId, displayName, normalizedName, position,
                accountId, guestRef, Instant.now());
        QueueEntry saved = repository.save(entry);
        log.info("Entry {} joined event {} at position {}", saved.getId(), eventId, position);
        return saved;
    }

    // ------------------------------------------------------------------
    // 9.3 — staff-entered stub
    // ------------------------------------------------------------------

    /**
     * Adds an entry on behalf of a participant who is not authenticated (spec
     * "Записи, созданные персоналом за участника"; task 9.3): {@code origin =
     * STAFF}, no account and no guest session, at the end of the queue.
     *
     * <p>The name is validated exactly like a joining name (400
     * {@code invalid_name}); it occupies the name for everyone else, so a later
     * guest join under it is a 409 {@code name_taken}.</p>
     */
    @Transactional
    public QueueEntry addStaffEntry(UUID eventId, ParsedToken token, String requestedName) {
        requireStaff(token);
        Event event = lockEvent(eventId);
        requireAccess(token, event);

        String name = requireGuestName(requestedName);
        validateNameLength(name);
        requireJoinable(event);

        String normalizedName = QueueNameNormalizer.normalize(name);
        if (repository.existsByEventIdAndNameNormalizedAndStatusIn(eventId, normalizedName, ACTIVE)) {
            throw ApiException.conflict("name_taken", "Это имя уже занято в очереди");
        }
        if (repository.countByEventIdAndStatusIn(eventId, ACTIVE) >= event.getEntryLimit()) {
            throw ApiException.conflict("queue_full", "Очередь заполнена");
        }

        int position = repository.findFirstByEventIdAndStatusInOrderByPositionDesc(eventId, ACTIVE)
                .map(QueueEntry::getPosition)
                .orElse(0) + 1;
        QueueEntry entry = QueueEntry.staffStub(
                UUID.randomUUID(), eventId, name, normalizedName, position, Instant.now());
        QueueEntry saved = repository.save(entry);
        log.info("Staff entry {} created in event {} at position {}", saved.getId(), eventId, position);
        return saved;
    }

    // ------------------------------------------------------------------
    // 9.2 — carry-over
    // ------------------------------------------------------------------

    /**
     * Carries active entries of a source event into the target event (spec "Хвост";
     * design.md D18; task 9.2).
     *
     * <p>Account-bound sources keep their account binding; guest and staff-stub
     * sources become placeholders ({@code holderAccountId = null},
     * {@code guestRef = null}, same name). Names already taken in the target are
     * skipped with {@code name_taken}; once the target limit is reached the rest is
     * skipped with {@code queue_full}. Everything runs in one transaction under the
     * target event's pessimistic lock, so concurrent carry-overs cannot violate the
     * name/position indexes.</p>
     */
    @Transactional
    public CarryOverResponse carryOver(UUID targetEventId, ParsedToken token, CarryOverRequest request) {
        requireStaff(token);
        Objects.requireNonNull(request, "request");
        if (request.sourceEventId() == null) {
            throw ApiException.badRequest("invalid_source", "Исходное событие обязательно");
        }

        Event target = lockEvent(targetEventId);
        requireAccess(token, target);
        if (target.getStatus() != EventStatus.OPEN) {
            throw ApiException.conflict("event_closed", "Целевое событие закрыто");
        }

        Event source = eventRepository.findById(request.sourceEventId())
                .orElseThrow(() -> ApiException.notFound("event_not_found", "Исходное событие не найдено"));
        List<QueueEntry> candidates = selectCarryOverCandidates(source, request.entryIds());

        List<QueueEntry> targetActive =
                repository.findByEventIdAndStatusInOrderByPositionAsc(targetEventId, ACTIVE);
        Set<String> takenNames = new HashSet<>();
        for (QueueEntry active : targetActive) {
            takenNames.add(active.getNameNormalized());
        }
        int nextPosition = targetActive.stream()
                .mapToInt(QueueEntry::getPosition)
                .max()
                .orElse(0) + 1;
        int activeCount = targetActive.size();
        int limit = target.getEntryLimit();

        List<CarryOverResponse.Added> added = new ArrayList<>();
        List<CarryOverResponse.Skipped> skipped = new ArrayList<>();
        for (QueueEntry candidate : candidates) {
            if (takenNames.contains(candidate.getNameNormalized())) {
                skipped.add(new CarryOverResponse.Skipped(
                        candidate.getId(), candidate.getName(), "name_taken"));
                continue;
            }
            if (activeCount >= limit) {
                skipped.add(new CarryOverResponse.Skipped(
                        candidate.getId(), candidate.getName(), "queue_full"));
                continue;
            }
            QueueEntry carried = QueueEntry.carryOver(
                    UUID.randomUUID(),
                    targetEventId,
                    candidate.getName(),
                    candidate.getNameNormalized(),
                    nextPosition,
                    candidate.getHolderAccountId(),
                    Instant.now());
            QueueEntry saved = repository.save(carried);
            takenNames.add(candidate.getNameNormalized());
            nextPosition++;
            activeCount++;
            added.add(new CarryOverResponse.Added(
                    candidate.getId(), saved.getId(), candidate.getName()));
        }
        log.info("Carry-over into event {}: {} added, {} skipped",
                targetEventId, added.size(), skipped.size());
        return new CarryOverResponse(added, skipped);
    }

    private List<QueueEntry> selectCarryOverCandidates(Event source, List<UUID> entryIds) {
        if (entryIds == null || entryIds.isEmpty()) {
            return repository.findByEventIdAndStatusInOrderByPositionAsc(source.getId(), ACTIVE);
        }
        var unique = new LinkedHashSet<>(entryIds);
        List<QueueEntry> candidates = new ArrayList<>(unique.size());
        for (UUID entryId : unique) {
            if (entryId == null) {
                throw ApiException.badRequest("invalid_entry", "Некорректная запись источника");
            }
            QueueEntry entry = repository.findByIdAndEventId(entryId, source.getId())
                    .orElseThrow(() -> ApiException.badRequest("invalid_entry",
                            "Запись не принадлежит исходному событию"));
            if (!entry.getStatus().isActive()) {
                throw ApiException.badRequest("invalid_entry",
                        "Переносить можно только активные записи");
            }
            candidates.add(entry);
        }
        return candidates;
    }

    // ------------------------------------------------------------------
    // 8.8 — projection
    // ------------------------------------------------------------------

    /** @return the current queue/journal projection for the caller's role */
    @Transactional(readOnly = true)
    public QueueStateResponse state(UUID eventId, ParsedToken token) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> ApiException.notFound("event_not_found", "Событие не найдено"));
        requireAccess(token, event);
        return projection.state(event, token);
    }

    // ------------------------------------------------------------------
    // 8.4 — "next"
    // ------------------------------------------------------------------

    /**
     * Marks the first {@code WAITING} entry (skipping paused ones) as passed and
     * publishes both domain events in the same transaction.
     */
    @Transactional
    public QueueStateResponse advance(UUID eventId, ParsedToken token) {
        requireStaff(token);
        Event event = lockEvent(eventId);
        requireAccess(token, event);
        if (event.getStatus() != EventStatus.OPEN) {
            throw ApiException.conflict("event_closed", "Продвижение очереди недоступно");
        }

        QueueEntry head = repository
                .findFirstByEventIdAndStatusOrderByPositionAsc(eventId, QueueEntryStatus.WAITING)
                .orElseThrow(() -> ApiException.conflict("queue_empty",
                        "В очереди нет ожидающих записей"));

        UUID actor = requireAccountId(token);
        Instant passedAt = Instant.now();
        head.pass(passedAt, actor);
        repository.save(head);

        QueueEntry next = repository
                .findFirstByEventIdAndStatusOrderByPositionAsc(eventId, QueueEntryStatus.WAITING)
                .orElse(null);

        outboxWriter.write(DomainEvent.of(
                EventTypes.ENTRY_PASSED,
                new EntryPassedPayload(eventId, head.getId(), head.getName(), passedAt, actor)));
        outboxWriter.write(DomainEvent.of(
                EventTypes.QUEUE_ADVANCED,
                new QueueAdvancedPayload(
                        eventId,
                        head.getId(),
                        head.getName(),
                        next == null ? null : next.getId())));
        log.info("Entry {} passed in event {} by {}", head.getId(), eventId, actor);

        return projection.state(event, token);
    }

    // ------------------------------------------------------------------
    // 8.5 — pause / resume
    // ------------------------------------------------------------------

    /** {@code WAITING -> PAUSED}; the position is kept. */
    @Transactional
    public QueueStateResponse pause(UUID eventId, UUID entryId, ParsedToken token) {
        return changeStatus(eventId, entryId, token, true);
    }

    /** {@code PAUSED -> WAITING}; the position is kept. */
    @Transactional
    public QueueStateResponse resume(UUID eventId, UUID entryId, ParsedToken token) {
        return changeStatus(eventId, entryId, token, false);
    }

    // ------------------------------------------------------------------
    // 8.6 — reordering
    // ------------------------------------------------------------------

    /**
     * Moves an active entry to {@code newPosition} and renumbers all active entries
     * {@code 1..K}. Passed rows are not touched: their position is historical and
     * outside the partial unique index.
     */
    @Transactional
    public QueueStateResponse reorder(UUID eventId, UUID entryId, int newPosition, ParsedToken token) {
        requireStaff(token);
        Event event = lockEvent(eventId);
        requireAccess(token, event);
        requireOpenEvent(event);

        QueueEntry moved = requireEntry(eventId, entryId);
        if (!moved.getStatus().isActive()) {
            throw ApiException.conflict("invalid_state", "Сдавшую запись нельзя переставлять");
        }

        List<QueueEntry> active =
                repository.findByEventIdAndStatusInOrderByPositionAsc(eventId, ACTIVE);
        int total = active.size();
        if (newPosition < 1 || newPosition > total) {
            throw ApiException.badRequest("invalid_position",
                    "Позиция должна быть в диапазоне 1.." + total);
        }

        List<UUID> order = new ArrayList<>(active.stream().map(QueueEntry::getId).toList());
        order.remove(moved.getId());
        order.add(newPosition - 1, moved.getId());

        // Two-phase renumbering: park every active position far above the final
        // range, then stamp the final 1..K values. A single pass could transiently
        // collide on the partial unique index.
        repository.shiftActivePositions(eventId, ACTIVE, REORDER_OFFSET);
        for (int index = 0; index < order.size(); index++) {
            repository.updatePosition(order.get(index), index + 1);
        }
        log.info("Entry {} moved to position {} in event {}", entryId, newPosition, eventId);
        return projection.state(event, token);
    }

    // ------------------------------------------------------------------
    // 8.7 — removal (staff) and self-exit
    // ------------------------------------------------------------------

    /** Deletes an active entry: staff any, students/guests only their own. */
    @Transactional
    public void delete(UUID eventId, UUID entryId, ParsedToken token) {
        Event event = lockEvent(eventId);
        requireAccess(token, event);
        requireOpenEvent(event);

        QueueEntry entry = requireEntry(eventId, entryId);
        if (!entry.getStatus().isActive()) {
            throw ApiException.conflict("invalid_state", "Сдавшую запись нельзя удалить");
        }
        if (!accessService.isStaff(token) && !isOwnEntry(entry, token)) {
            throw ApiException.forbidden("entry_forbidden", "Можно удалить только свою запись");
        }
        repository.delete(entry);
        log.info("Entry {} removed from event {} by {}", entryId, eventId, token.subject());
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private QueueStateResponse changeStatus(UUID eventId, UUID entryId, ParsedToken token, boolean pause) {
        requireStaff(token);
        Event event = lockEvent(eventId);
        requireAccess(token, event);
        requireOpenEvent(event);

        QueueEntry entry = requireEntry(eventId, entryId);
        if (pause) {
            if (entry.getStatus() != QueueEntryStatus.WAITING) {
                throw ApiException.conflict("invalid_state",
                        "Приостановить можно только ожидающую запись");
            }
            entry.pause();
        } else {
            if (entry.getStatus() != QueueEntryStatus.PAUSED) {
                throw ApiException.conflict("invalid_state",
                        "Вернуть в очередь можно только приостановленную запись");
            }
            entry.resume();
        }
        repository.save(entry);
        return projection.state(event, token);
    }

    private Event lockEvent(UUID eventId) {
        Objects.requireNonNull(eventId, "eventId");
        return eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> ApiException.notFound("event_not_found", "Событие не найдено"));
    }

    private QueueEntry requireEntry(UUID eventId, UUID entryId) {
        Objects.requireNonNull(entryId, "entryId");
        return repository.findByIdAndEventId(entryId, eventId)
                .orElseThrow(() -> ApiException.notFound("entry_not_found", "Запись не найдена"));
    }

    private void requireStaff(ParsedToken token) {
        if (!accessService.isStaff(token)) {
            throw ApiException.forbidden("event_forbidden", "Недостаточно прав для управления очередью");
        }
    }

    private void requireAccess(ParsedToken token, Event event) {
        if (!accessService.canView(token, event.getAvailability())) {
            throw ApiException.forbidden("event_forbidden", "Недостаточно прав для доступа к событию");
        }
    }

    private void requireJoinable(Event event) {
        if (event.getStatus() != EventStatus.OPEN) {
            throw ApiException.conflict("event_closed", "Приём записей закрыт");
        }
    }

    /**
     * A {@code CLOSED}/{@code ARCHIVED} event is read-only: every queue mutation
     * (pause, resume, reorder, delete, advance, staff add, carry-over) is rejected
     * with 409 {@code event_closed} before the affected entry is even looked up.
     */
    private static void requireOpenEvent(Event event) {
        if (event.getStatus() != EventStatus.OPEN) {
            throw ApiException.conflict("event_closed", "Событие закрыто для изменений очереди");
        }
    }

    private String resolveAccountName(UUID accountId, String requestedName) {
        if (requestedName != null && !requestedName.isBlank()) {
            return requestedName.trim();
        }
        return profileClient.fetchFullName(accountId);
    }

    private static String requireGuestName(String requestedName) {
        if (requestedName == null || requestedName.isBlank()) {
            throw ApiException.badRequest("invalid_name", "Укажите имя записи");
        }
        return requestedName.trim();
    }

    private static void validateNameLength(String name) {
        if (name.isEmpty() || name.length() > QueueNameNormalizer.MAX_LENGTH) {
            throw ApiException.badRequest("invalid_name",
                    "Имя записи должно содержать от 1 до " + QueueNameNormalizer.MAX_LENGTH + " символов");
        }
    }

    private boolean isOwnEntry(QueueEntry entry, ParsedToken token) {
        if (isGuest(token)) {
            return entry.getGuestRef() != null && entry.getGuestRef().equals(guestRefOf(token));
        }
        UUID accountId = requireAccountId(token);
        return entry.getHolderAccountId() != null && entry.getHolderAccountId().equals(accountId);
    }

    private static boolean isGuest(ParsedToken token) {
        return token.roles().contains(RoleNames.GUEST)
                && token.subject().startsWith(GUEST_SUBJECT_PREFIX);
    }

    private static UUID guestRefOf(ParsedToken token) {
        try {
            return UUID.fromString(token.subject().substring(GUEST_SUBJECT_PREFIX.length()));
        } catch (RuntimeException ex) {
            throw ApiException.unauthorized("unauthorized", "Некорректная гостевая сессия");
        }
    }

    private static UUID requireAccountId(ParsedToken token) {
        try {
            return UUID.fromString(Objects.requireNonNull(token, "token").subject());
        } catch (IllegalArgumentException ex) {
            throw ApiException.unauthorized("unauthorized", "Требуется аутентификация");
        }
    }
}
