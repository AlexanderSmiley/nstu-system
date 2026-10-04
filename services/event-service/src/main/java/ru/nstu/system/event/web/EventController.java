package ru.nstu.system.event.web;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.nstu.system.event.error.ApiException;
import ru.nstu.system.event.service.EventArchiveService;
import ru.nstu.system.event.service.EventService;
import ru.nstu.system.event.web.dto.CreateEventRequest;
import ru.nstu.system.event.web.dto.EventDetailResponse;
import ru.nstu.system.event.web.dto.EventResponse;
import ru.nstu.system.event.web.dto.HistoryEventResponse;
import ru.nstu.system.event.web.dto.UpdateEventRequest;
import ru.nstu.system.security.ParsedToken;
import ru.nstu.system.security.SecurityContextSupport;

/**
 * Event REST API mounted under {@code /api/events} (design.md D4, D10; tasks
 * 7.1-7.7).
 *
 * <p>The security filter chain requires a valid access token for every route
 * here, including the guest-accessible ones: an anonymous call to
 * {@code /api/events/by-slug/{slug}} is answered with 401 so the SPA can show the
 * login screen with the "enter as guest" button (task 7.4). Role- and
 * state-dependent rules live in {@link EventService}.</p>
 */
@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;

    private final EventArchiveService archiveService;

    public EventController(EventService eventService, EventArchiveService archiveService) {
        this.eventService = eventService;
        this.archiveService = archiveService;
    }

    /** Creates an event; staff and administrators only. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventResponse create(@Valid @RequestBody CreateEventRequest request) {
        return EventResponse.from(eventService.createEvent(requireToken(), request));
    }

    /** Lists the caller-visible {@code OPEN} events of the default group. */
    @GetMapping
    public List<EventResponse> list() {
        return eventService.visibleEvents(requireToken()).stream()
                .map(EventResponse::from)
                .toList();
    }

    /**
     * Role-scoped event history (task 9.7): staff see {@code CLOSED}, administrators
     * also see {@code ARCHIVED}, students/guests get an empty list.
     */
    @GetMapping("/history")
    public List<HistoryEventResponse> history() {
        return eventService.history(requireToken());
    }

    /**
     * Event detail with the (role-dependent) surrender journal (task 9.1).
     * Archived and unknown events are 404; an event stricter than the caller role
     * is 403.
     */
    @GetMapping("/{id}")
    public EventDetailResponse detail(@PathVariable UUID id) {
        return eventService.eventDetail(id, requireToken());
    }

    /**
     * Resolves a short link. Archived or unknown slugs are 404; an event stricter
     * than the caller role is 403. The response never contains queue state.
     */
    @GetMapping("/by-slug/{slug}")
    public EventResponse bySlug(@PathVariable String slug) {
        return EventResponse.from(eventService.eventBySlug(slug, requireToken()));
    }

    /** Applies the role-dependent edit matrix. */
    @PatchMapping("/{id}")
    public EventResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateEventRequest request) {
        return EventResponse.from(eventService.updateEvent(id, requireToken(), request));
    }

    /** Closes the event and publishes {@code event.closed}. */
    @PostMapping("/{id}/close")
    public EventResponse close(@PathVariable UUID id) {
        return EventResponse.from(eventService.closeEvent(id, requireToken()));
    }

    /** Re-opens a closed event, restarting the retention countdown. */
    @PostMapping("/{id}/open")
    public EventResponse open(@PathVariable UUID id) {
        return EventResponse.from(eventService.openEvent(id, requireToken()));
    }

    /** Archives a {@code CLOSED} event, publishing {@code event.archived} (task 9.4). */
    @PostMapping("/{id}/archive")
    public EventResponse archive(@PathVariable UUID id) {
        return EventResponse.from(archiveService.archive(id, requireToken()));
    }

    /** Restores an archived event and its rows; administrator only (task 9.6). */
    @PostMapping("/{id}/restore")
    public EventResponse restore(@PathVariable UUID id) {
        return EventResponse.from(archiveService.restore(id, requireToken()));
    }

    /** Permanently deletes an archived event; administrator only (task 9.6). */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        archiveService.delete(id, requireToken());
    }

    private static ParsedToken requireToken() {
        return SecurityContextSupport.currentToken()
                .orElseThrow(() -> ApiException.unauthorized("unauthorized", "Требуется аутентификация"));
    }
}
