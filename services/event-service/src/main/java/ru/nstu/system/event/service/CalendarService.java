package ru.nstu.system.event.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.nstu.system.contracts.Groups;
import ru.nstu.system.event.domain.Audience;
import ru.nstu.system.event.domain.CalendarEntry;
import ru.nstu.system.event.domain.CalendarEntryRepository;
import ru.nstu.system.event.error.ApiException;
import ru.nstu.system.event.web.dto.CalendarEntryResponse;
import ru.nstu.system.event.web.dto.CreateCalendarEntryRequest;
import ru.nstu.system.event.web.dto.UpdateCalendarEntryRequest;
import ru.nstu.system.security.ParsedToken;

/**
 * Calendar module rules (change add-calendar-module; design.md D3, D4).
 *
 * <p>Authorisation lives here rather than in the controller so both the window
 * query and the mutations share one source of truth: the filter chain only proves
 * the token is valid. The guest takes a short-circuit path — an empty calendar,
 * never an error — while students and above are filtered by role in the SQL
 * repository query.</p>
 */
@Service
public class CalendarService {

    private static final Logger log = LoggerFactory.getLogger(CalendarService.class);

    /** Maximum inclusive window the listing accepts (design.md D4). */
    static final int MAX_WINDOW_DAYS = 31;

    /** Rank of {@code STUDENT}: the lowest role allowed to create and to see {@code GROUP}. */
    static final int STUDENT_RANK = 2;

    /** Rank of {@code STAFF}: the lowest role allowed to see {@code STAFF} entries. */
    static final int STAFF_RANK = 3;

    static final int TITLE_MAX_LENGTH = 200;

    /** Maximum length of an optional description (change add-preferences-and-calendar-ui, task 5.x). */
    static final int DESCRIPTION_MAX_LENGTH = 150;

    private final CalendarEntryRepository repository;

    private final EventAccessService accessService;

    private final StudentProfileClient profileClient;

    public CalendarService(CalendarEntryRepository repository,
                           EventAccessService accessService,
                           StudentProfileClient profileClient) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.accessService = Objects.requireNonNull(accessService, "accessService");
        this.profileClient = Objects.requireNonNull(profileClient, "profileClient");
    }

    // ------------------------------------------------------------------
    // GET /api/calendar
    // ------------------------------------------------------------------

    /**
     * Returns the entries of the requested window visible to the caller.
     *
     * <p>The window is mandatory and validated first (so a malformed request is
     * answered with {@code invalid_date} for every role). A guest then gets an
     * empty list without touching the database; everyone else is filtered by
     * author and audience inside the query.</p>
     */
    @Transactional(readOnly = true)
    public List<CalendarEntryResponse> visibleEntries(ParsedToken token, String fromRaw, String toRaw) {
        Window window = parseWindow(fromRaw, toRaw);
        int rank = accessService.rank(token);
        if (rank < STUDENT_RANK) {
            return List.of();
        }
        UUID viewerId = accountIdOrNull(token);
        return repository
                .findVisible(Groups.DEFAULT_GROUP_ID, window.from(), window.to(), viewerId, visibleAudiences(rank))
                .stream()
                .map(entry -> CalendarEntryResponse.from(entry, viewerId))
                .toList();
    }

    // ------------------------------------------------------------------
    // POST /api/calendar
    // ------------------------------------------------------------------

    /**
     * Creates an entry on behalf of a caller of rank {@code STUDENT} or above.
     *
     * <p>A guest is rejected with {@code forbidden}. When the request carries the
     * displayed window, a {@code startsOn} outside of it is rejected with
     * {@code invalid_date}; a student asking for {@code STAFF} is rejected with
     * {@code invalid_audience}.</p>
     */
    @Transactional
    public CalendarEntryResponse create(ParsedToken token, CreateCalendarEntryRequest request) {
        Objects.requireNonNull(request, "request");
        if (accessService.rank(token) < STUDENT_RANK) {
            throw ApiException.forbidden("forbidden", "Недостаточно прав для создания мероприятия");
        }
        String title = validateTitle(request.title());
        String description = validateDescription(request.description());
        LocalDate startsOn = requireStartsOn(request.startsOn(), request.from(), request.to());
        Audience audience = request.audience() == null ? Audience.ME : request.audience();
        validateAudience(token, audience);
        UUID author = requireAccountId(token);
        String authorDisplayName = profileClient.tryFetchFullName(author);

        CalendarEntry entry = CalendarEntry.create(
                UUID.randomUUID(),
                Groups.DEFAULT_GROUP_ID,
                author,
                authorDisplayName,
                title,
                description,
                startsOn,
                request.startsAt(),
                audience,
                Instant.now());
        CalendarEntry saved = repository.save(entry);
        log.info("Created calendar entry {} for {} (audience {})", saved.getId(), saved.getStartsOn(), audience);
        return CalendarEntryResponse.from(saved, author);
    }

    // ------------------------------------------------------------------
    // PATCH /api/calendar/{id}
    // ------------------------------------------------------------------

    /**
     * Partially edits an entry (change add-preferences-and-calendar-ui; design.md
     * D6): its author always, an administrator any, everyone else {@code 403}; an
     * unknown id is {@code 404}.
     *
     * <p>Only the supplied fields are changed. Validation mirrors {@link #create}
     * ({@code invalid_title}, {@code invalid_date}, {@code invalid_audience}) and
     * runs after the authorisation check, so a foreign caller cannot probe
     * validation. The author snapshot is never touched.</p>
     */
    @Transactional
    public CalendarEntryResponse update(UUID id, ParsedToken token, UpdateCalendarEntryRequest request) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(request, "request");
        CalendarEntry entry = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("calendar_not_found", "Мероприятие не найдено"));
        UUID viewerId = accountIdOrNull(token);
        if (!accessService.isAdmin(token) && !entry.getAuthorAccountId().equals(viewerId)) {
            throw ApiException.forbidden("calendar_forbidden", "Недостаточно прав для изменения мероприятия");
        }

        String title = request.title() == null ? null : validateTitle(request.title());
        String description = request.description() == null ? null : validateDescription(request.description());
        LocalDate startsOn = request.startsOn() == null
                ? null
                : requireStartsOn(request.startsOn(), request.from(), request.to());
        Audience audience = request.audience();
        if (audience != null) {
            validateAudience(token, audience);
        }

        entry.update(title, description, startsOn, request.startsAt(), audience, Instant.now());
        CalendarEntry saved = repository.save(entry);
        log.info("Updated calendar entry {} (audience {})", saved.getId(), saved.getAudience());
        return CalendarEntryResponse.from(saved, viewerId);
    }

    // ------------------------------------------------------------------
    // DELETE /api/calendar/{id}
    // ------------------------------------------------------------------

    /**
     * Deletes an entry: its author always, an administrator any, staff and
     * students only their own (otherwise {@code 403}); unknown id is {@code 404}.
     */
    @Transactional
    public void delete(UUID id, ParsedToken token) {
        Objects.requireNonNull(id, "id");
        CalendarEntry entry = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("calendar_not_found", "Мероприятие не найдено"));
        UUID viewerId = accountIdOrNull(token);
        if (!accessService.isAdmin(token) && !entry.getAuthorAccountId().equals(viewerId)) {
            throw ApiException.forbidden("calendar_forbidden", "Недостаточно прав для удаления мероприятия");
        }
        repository.delete(entry);
        log.info("Deleted calendar entry {}", id);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static Set<Audience> visibleAudiences(int rank) {
        Set<Audience> audiences = new LinkedHashSet<>();
        audiences.add(Audience.GROUP);
        if (rank >= STAFF_RANK) {
            audiences.add(Audience.STAFF);
        }
        return audiences;
    }

    private static Window parseWindow(String fromRaw, String toRaw) {
        LocalDate from = parseDate(fromRaw);
        LocalDate to = parseDate(toRaw);
        validateWindow(from, to);
        return new Window(from, to);
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            throw invalidDate("Некорректная дата");
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException ex) {
            throw invalidDate("Некорректная дата");
        }
    }

    private static void validateWindow(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw invalidDate("Дата окончания окна не может быть раньше даты начала");
        }
        long length = ChronoUnit.DAYS.between(from, to) + 1;
        if (length > MAX_WINDOW_DAYS) {
            throw invalidDate("Окно календаря не должно превышать " + MAX_WINDOW_DAYS + " дней");
        }
    }

    private static LocalDate requireStartsOn(LocalDate startsOn, LocalDate from, LocalDate to) {
        if (startsOn == null) {
            throw invalidDate("Дата мероприятия обязательна");
        }
        boolean hasWindow = from != null || to != null;
        if (!hasWindow) {
            // No displayed window supplied: only the date's validity can be checked.
            return startsOn;
        }
        if (from == null || to == null) {
            throw invalidDate("Некорректное окно календаря");
        }
        validateWindow(from, to);
        if (startsOn.isBefore(from) || startsOn.isAfter(to)) {
            throw invalidDate("Дата мероприятия должна находиться в отображаемом окне календаря");
        }
        return startsOn;
    }

    private void validateAudience(ParsedToken token, Audience audience) {
        if (audience == Audience.STAFF && accessService.rank(token) < STAFF_RANK) {
            throw ApiException.badRequest("invalid_audience", "Этот адресат недоступен для вашей роли");
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
     * Validates the optional description: {@code null} and the empty string are
     * allowed ("описание необязательно"), anything longer than
     * {@link #DESCRIPTION_MAX_LENGTH} characters is rejected with
     * {@code invalid_description}. The value itself is never trimmed, so the
     * stored text is exactly what the author typed.
     */
    private static String validateDescription(String description) {
        if (description != null && description.length() > DESCRIPTION_MAX_LENGTH) {
            throw ApiException.badRequest("invalid_description",
                    "Описание не должно превышать " + DESCRIPTION_MAX_LENGTH + " символов");
        }
        return description;
    }

    private static UUID requireAccountId(ParsedToken token) {
        try {
            return UUID.fromString(Objects.requireNonNull(token, "token").subject());
        } catch (RuntimeException ex) {
            throw ApiException.unauthorized("unauthorized", "Требуется аутентификация");
        }
    }

    private static UUID accountIdOrNull(ParsedToken token) {
        try {
            return UUID.fromString(Objects.requireNonNull(token, "token").subject());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static ApiException invalidDate(String message) {
        return ApiException.badRequest("invalid_date", message);
    }

    /** Validated inclusive date window. */
    private record Window(LocalDate from, LocalDate to) {
    }
}
