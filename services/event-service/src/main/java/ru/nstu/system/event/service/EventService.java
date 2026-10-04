package ru.nstu.system.event.service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.nstu.system.contracts.Groups;
import ru.nstu.system.contracts.events.DomainEvent;
import ru.nstu.system.contracts.events.EventClosedPayload;
import ru.nstu.system.contracts.events.EventTypes;
import ru.nstu.system.contracts.outbox.OutboxWriter;
import ru.nstu.system.event.domain.Availability;
import ru.nstu.system.event.domain.EntryUnit;
import ru.nstu.system.event.domain.Event;
import ru.nstu.system.event.domain.EventRepository;
import ru.nstu.system.event.domain.EventStatus;
import ru.nstu.system.event.domain.JournalVisibility;
import ru.nstu.system.event.error.ApiException;
import ru.nstu.system.event.web.dto.CreateEventRequest;
import ru.nstu.system.event.web.dto.EventDetailResponse;
import ru.nstu.system.event.web.dto.HistoryEventResponse;
import ru.nstu.system.event.web.dto.UpdateEventRequest;
import ru.nstu.system.security.ParsedToken;

/**
 * Event lifecycle: creation with defaults, editing matrix, opening/closing and
 * the role-filtered active list (tasks 7.1-7.7; design.md D10, D15, D16, D24).
 *
 * <p>Authorisation is enforced here rather than in the controllers so that every
 * entry point (REST today, internal callers tomorrow) shares the same rules: the
 * security filter chain only proves the token is valid, not that the role may
 * perform the action.</p>
 *
 * <p>Closing writes {@code event.closed} to the outbox <em>in the same
 * transaction</em> as the status change, which is what makes the outbox pattern
 * correct (design.md D13).</p>
 */
@Service
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    static final int DEFAULT_ENTRY_LIMIT = 27;

    /** Upper bound of the configurable queue entry limit (task 7.2). */
    static final int MAX_ENTRY_LIMIT = 1000;

    static final int DEFAULT_RETENTION_DAYS = 14;

    /** Staff may keep a closed event for at most this many days (spec "Срок хранения"). */
    static final int STAFF_MAX_RETENTION_DAYS = 30;

    static final int TITLE_MAX_LENGTH = 200;

    private final EventRepository repository;

    private final QueueEntryCounter queueEntryCounter;

    private final OutboxWriter outboxWriter;

    private final SlugGenerator slugGenerator;

    private final EventAccessService accessService;

    private final QueueProjection projection;

    public EventService(EventRepository repository,
                        QueueEntryCounter queueEntryCounter,
                        OutboxWriter outboxWriter,
                        SlugGenerator slugGenerator,
                        EventAccessService accessService,
                        QueueProjection projection) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.queueEntryCounter = Objects.requireNonNull(queueEntryCounter, "queueEntryCounter");
        this.outboxWriter = Objects.requireNonNull(outboxWriter, "outboxWriter");
        this.slugGenerator = Objects.requireNonNull(slugGenerator, "slugGenerator");
        this.accessService = Objects.requireNonNull(accessService, "accessService");
        this.projection = Objects.requireNonNull(projection, "projection");
    }

    // ------------------------------------------------------------------
    // 7.1 / 7.2 — creation
    // ------------------------------------------------------------------

    /**
     * Creates a {@code QUEUE} event owned by the default group.
     *
     * <p>Defaults (availability {@code GUEST+}, limit 27, unit {@code BRIGADE},
     * journal {@code STAFF}, retention 14) are applied for any omitted field. The
     * event type is fixed to {@code QUEUE}; a different type never reaches this
     * method because the request enum rejects it during JSON binding.</p>
     */
    @Transactional
    public Event createEvent(ParsedToken token, CreateEventRequest request) {
        Objects.requireNonNull(request, "request");
        requireStaff(token);

        String title = validateTitle(request.title());
        Availability availability = request.availability() == null
                ? Availability.GUEST_PLUS : request.availability();
        int entryLimit = request.entryLimit() == null ? DEFAULT_ENTRY_LIMIT : request.entryLimit();
        validateEntryLimit(entryLimit);
        EntryUnit entryUnit = request.entryUnit() == null ? EntryUnit.BRIGADE : request.entryUnit();
        JournalVisibility journalVisibility = request.journalVisibility() == null
                ? JournalVisibility.STAFF : request.journalVisibility();
        int retentionDays = request.retentionDays() == null
                ? DEFAULT_RETENTION_DAYS : request.retentionDays();
        validateRetention(retentionDays, accessService.isAdmin(token));

        String slug = slugGenerator.generate(title, repository::existsBySlug);
        Event event = Event.create(
                UUID.randomUUID(),
                Groups.DEFAULT_GROUP_ID,
                title,
                request.description(),
                availability,
                request.startsAt(),
                entryLimit,
                entryUnit,
                journalVisibility,
                retentionDays,
                slug,
                requireAccountId(token),
                Instant.now());
        Event saved = repository.save(event);
        log.info("Created event {} (slug {}) by {}", saved.getId(), saved.getSlug(), token.subject());
        return saved;
    }

    // ------------------------------------------------------------------
    // 7.7 — visible active list
    // ------------------------------------------------------------------

    /** @return {@code OPEN} events of the default group visible to the token role */
    @Transactional(readOnly = true)
    public List<Event> visibleEvents(ParsedToken token) {
        List<Availability> visible = accessService.visibleAvailabilities(token);
        return repository.findByStatusAndGroupIdAndAvailabilityInOrderByCreatedAtDesc(
                EventStatus.OPEN, Groups.DEFAULT_GROUP_ID, visible);
    }

    // ------------------------------------------------------------------
    // 7.3 / 7.4 — short link
    // ------------------------------------------------------------------

    /**
     * Resolves a short link for an authenticated caller.
     *
     * <p>An archived event is a 404 to everyone; an existing but too strict event
     * is a 403. The returned entity never carries queue state (the queue is served
     * by a dedicated endpoint in group 8).</p>
     */
    @Transactional(readOnly = true)
    public Event eventBySlug(String slug, ParsedToken token) {
        Event event = repository.findBySlug(slug)
                .orElseThrow(() -> ApiException.notFound("event_not_found", "Событие не найдено"));
        if (event.getStatus() == EventStatus.ARCHIVED) {
            // Archived links are hidden from everyone, including administrators.
            throw ApiException.notFound("event_not_found", "Событие не найдено");
        }
        if (!accessService.canView(token, event.getAvailability())) {
            throw ApiException.forbidden("event_forbidden", "Недостаточно прав для просмотра события");
        }
        return event;
    }

    // ------------------------------------------------------------------
    // 9.1 — event detail with journal
    // ------------------------------------------------------------------

    /**
     * Returns one event together with its surrender journal (task 9.1).
     *
     * <p>Like the short link, an archived event is a 404 to everyone; an existing
     * but too strict event is a 403. The journal is {@code null} — and therefore
     * absent from the JSON — when the caller may not see it, reusing the same
     * projection as {@code GET /api/events/{id}/queue}.</p>
     */
    @Transactional(readOnly = true)
    public EventDetailResponse eventDetail(UUID id, ParsedToken token) {
        Objects.requireNonNull(id, "id");
        Event event = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("event_not_found", "Событие не найдено"));
        if (event.getStatus() == EventStatus.ARCHIVED) {
            throw ApiException.notFound("event_not_found", "Событие не найдено");
        }
        if (!accessService.canView(token, event.getAvailability())) {
            throw ApiException.forbidden("event_forbidden", "Недостаточно прав для просмотра события");
        }
        return EventDetailResponse.from(event, projection.journal(event, token));
    }

    // ------------------------------------------------------------------
    // 9.7 — history
    // ------------------------------------------------------------------

    /**
     * Role-scoped event history (task 9.7; spec "Восстановление и безвозвратное
     * удаление", scenario "Состав истории для разных ролей").
     *
     * <p>Staff see {@code CLOSED} events only; administrators additionally see
     * {@code ARCHIVED} ones. Students and guests get an empty list rather than a
     * 403 so the client can render an empty history page without special-casing.</p>
     */
    @Transactional(readOnly = true)
    public List<HistoryEventResponse> history(ParsedToken token) {
        if (!accessService.isStaff(token)) {
            return List.of();
        }
        List<EventStatus> statuses = accessService.isAdmin(token)
                ? List.of(EventStatus.CLOSED, EventStatus.ARCHIVED)
                : List.of(EventStatus.CLOSED);
        return repository
                .findByStatusInAndGroupIdOrderByClosedAtDesc(statuses, Groups.DEFAULT_GROUP_ID)
                .stream()
                .map(event -> HistoryEventResponse.from(event,
                        event.getStatus() == EventStatus.ARCHIVED
                                ? null
                                : queueEntryCounter.countAll(event.getId())))
                .toList();
    }

    // ------------------------------------------------------------------
    // 7.5 — editing matrix
    // ------------------------------------------------------------------

    /**
     * Applies the editable fields allowed for the caller's role.
     *
     * <p>Staff may change everything except {@code slug} and {@code retentionDays},
     * which are administrator-only; supplying either as staff is a 403. An
     * archived event cannot be modified at all (409).</p>
     */
    @Transactional
    public Event updateEvent(UUID id, ParsedToken token, UpdateEventRequest request) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(request, "request");
        requireStaff(token);

        Event event = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("event_not_found", "Событие не найдено"));

        boolean admin = accessService.isAdmin(token);
        if (!admin && (request.slug() != null || request.retentionDays() != null)) {
            throw ApiException.forbidden("event_forbidden",
                    "Короткую ссылку и срок хранения может менять только администратор");
        }
        if (event.getStatus() == EventStatus.ARCHIVED) {
            throw ApiException.conflict("event_archived", "Архивное событие нельзя изменить");
        }

        if (request.title() != null) {
            event.changeTitle(validateTitle(request.title()));
        }
        if (request.description() != null) {
            event.changeDescription(request.description());
        }
        if (request.availability() != null) {
            event.changeAvailability(request.availability());
        }
        if (request.startsAt() != null) {
            event.changeStartsAt(request.startsAt());
        }
        if (request.entryLimit() != null) {
            validateEntryLimit(request.entryLimit());
            // Lowering the limit never deletes existing entries (spec
            // "Уменьшение лимита ниже текущего числа записей").
            event.changeEntryLimit(request.entryLimit());
        }
        if (request.entryUnit() != null && request.entryUnit() != event.getEntryUnit()) {
            if (queueEntryCounter.hasActiveEntries(id)) {
                throw ApiException.conflict("entry_unit_locked",
                        "Единицу записи нельзя изменить, пока в очереди есть записи");
            }
            event.changeEntryUnit(request.entryUnit());
        }
        if (request.journalVisibility() != null) {
            event.changeJournalVisibility(request.journalVisibility());
        }
        if (request.retentionDays() != null) {
            validateRetention(request.retentionDays(), admin);
            event.changeRetentionDays(request.retentionDays());
        }
        if (request.slug() != null) {
            String slug = validateSlug(request.slug());
            if (!slug.equals(event.getSlug())) {
                ensureSlugAvailable(slug, event.getId());
                event.changeSlug(slug);
            }
        }
        return repository.save(event);
    }

    // ------------------------------------------------------------------
    // 7.6 — open / close
    // ------------------------------------------------------------------

    /**
     * Closes the event, starting the retention countdown and publishing
     * {@code event.closed} through the outbox in the same transaction.
     *
     * <p>Closing an already closed event is a no-op and publishes nothing, so a
     * retried request cannot emit duplicate events.</p>
     */
    @Transactional
    public Event closeEvent(UUID id, ParsedToken token) {
        Objects.requireNonNull(id, "id");
        requireStaff(token);

        Event event = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("event_not_found", "Событие не найдено"));
        if (event.getStatus() == EventStatus.ARCHIVED) {
            throw ApiException.conflict("event_archived", "Архивное событие нельзя закрыть");
        }
        if (event.getStatus() == EventStatus.CLOSED) {
            return event;
        }
        event.close(Instant.now());
        Event saved = repository.save(event);
        outboxWriter.write(DomainEvent.of(
                EventTypes.EVENT_CLOSED,
                new EventClosedPayload(saved.getId(), saved.getSlug(), saved.getTitle())));
        log.info("Closed event {} and queued event.closed", saved.getId());
        return saved;
    }

    /**
     * Re-opens a closed event, clearing {@code closed_at} so the retention
     * countdown restarts from the next close. No domain event is published.
     */
    @Transactional
    public Event openEvent(UUID id, ParsedToken token) {
        Objects.requireNonNull(id, "id");
        requireStaff(token);

        Event event = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("event_not_found", "Событие не найдено"));
        if (event.getStatus() == EventStatus.ARCHIVED) {
            throw ApiException.conflict("event_archived", "Архивное событие нельзя открыть");
        }
        if (event.getStatus() == EventStatus.OPEN) {
            return event;
        }
        event.open();
        return repository.save(event);
    }

    // ------------------------------------------------------------------
    // Validation helpers
    // ------------------------------------------------------------------

    private void requireStaff(ParsedToken token) {
        if (!accessService.isStaff(token)) {
            throw ApiException.forbidden("event_forbidden", "Недостаточно прав для управления событием");
        }
    }

    private static UUID requireAccountId(ParsedToken token) {
        try {
            return UUID.fromString(Objects.requireNonNull(token, "token").subject());
        } catch (IllegalArgumentException ex) {
            // Guest subjects are "guest:<uuid>" and can never create an event.
            throw ApiException.unauthorized("unauthorized", "Требуется аутентификация");
        }
    }

    private static String validateTitle(String title) {
        if (title == null || title.isBlank()) {
            throw ApiException.badRequest("invalid_title", "Название обязательно");
        }
        String trimmed = title.trim();
        if (trimmed.length() > TITLE_MAX_LENGTH) {
            throw ApiException.badRequest("invalid_title",
                    "Название не должно превышать " + TITLE_MAX_LENGTH + " символов");
        }
        return trimmed;
    }

    /**
     * Validates an explicitly supplied queue entry limit. A {@code null} value
     * means "not supplied": the create path then applies {@link #DEFAULT_ENTRY_LIMIT}
     * and the update path leaves the stored limit untouched.
     */
    private static void validateEntryLimit(Integer entryLimit) {
        if (entryLimit == null) {
            return;
        }
        if (entryLimit <= 0) {
            throw ApiException.badRequest("invalid_entry_limit", "Лимит записей должен быть положительным");
        }
        if (entryLimit > MAX_ENTRY_LIMIT) {
            throw ApiException.badRequest("invalid_entry_limit",
                    "Лимит записей не должен превышать " + MAX_ENTRY_LIMIT);
        }
    }

    private static void validateRetention(int days, boolean admin) {
        if (days < 1) {
            throw ApiException.badRequest("invalid_retention_days",
                    "Срок хранения должен быть не менее 1 дня");
        }
        if (!admin && days > STAFF_MAX_RETENTION_DAYS) {
            throw ApiException.badRequest("invalid_retention_days",
                    "Персонал может задавать срок хранения не более " + STAFF_MAX_RETENTION_DAYS + " дней");
        }
    }

    private static String validateSlug(String slug) {
        String trimmed = slug.trim();
        if (trimmed.isEmpty()
                || trimmed.length() > SlugGenerator.MAX_LENGTH
                || !SlugGenerator.VALID_SLUG_PATTERN.matcher(trimmed).matches()) {
            throw ApiException.badRequest("invalid_slug",
                    "Ссылка должна содержать только строчные латинские буквы, цифры и дефис (до "
                            + SlugGenerator.MAX_LENGTH + " символов)");
        }
        return trimmed;
    }

    private void ensureSlugAvailable(String slug, UUID selfId) {
        repository.findBySlug(slug)
                .filter(existing -> !existing.getId().equals(selfId))
                .ifPresent(existing -> {
                    throw ApiException.conflict("slug_taken", "Такая ссылка уже используется");
                });
    }
}
