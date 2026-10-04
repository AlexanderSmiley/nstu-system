package ru.nstu.system.event.web;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.nstu.system.event.error.ApiException;
import ru.nstu.system.event.service.CalendarService;
import ru.nstu.system.event.web.dto.CalendarEntryResponse;
import ru.nstu.system.event.web.dto.CreateCalendarEntryRequest;
import ru.nstu.system.event.web.dto.UpdateCalendarEntryRequest;
import ru.nstu.system.security.ParsedToken;
import ru.nstu.system.security.SecurityContextSupport;

/**
 * Calendar REST API mounted under {@code /api/calendar}
 * (change add-calendar-module; design.md D4).
 *
 * <p>The security filter chain requires a valid token for every route, including
 * the read one: guest sessions are authenticated and therefore allowed to open
 * the calendar, while an anonymous call is answered with {@code 401}. Role,
 * window and audience rules live in {@link CalendarService}.</p>
 */
@RestController
@RequestMapping("/api/calendar")
public class CalendarController {

    private final CalendarService calendarService;

    public CalendarController(CalendarService calendarService) {
        this.calendarService = calendarService;
    }

    /**
     * Lists the entries of the requested window visible to the caller. The window
     * is required and at most 31 days long; a malformed request is a
     * {@code 400 invalid_date}.
     */
    @GetMapping
    public List<CalendarEntryResponse> list(
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to) {
        return calendarService.visibleEntries(requireToken(), from, to);
    }

    /** Creates an entry on a day; {@code STUDENT} and above, guest is {@code 403}. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CalendarEntryResponse create(@RequestBody CreateCalendarEntryRequest request) {
        return calendarService.create(requireToken(), request);
    }

    /**
     * Partially edits an entry: author always, administrator any, others
     * {@code 403}; unknown id is {@code 404}. The body may carry any subset of
     * {@code title}/{@code description}/{@code startsOn}/{@code startsAt}/
     * {@code audience}; {@code from}/{@code to} describe the displayed window.
     */
    @PatchMapping("/{id}")
    public CalendarEntryResponse update(@PathVariable UUID id,
                                        @RequestBody UpdateCalendarEntryRequest request) {
        return calendarService.update(id, requireToken(), request);
    }

    /** Deletes an entry: author always, administrator any, others {@code 403}. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        calendarService.delete(id, requireToken());
    }

    private static ParsedToken requireToken() {
        return SecurityContextSupport.currentToken()
                .orElseThrow(() -> ApiException.unauthorized("unauthorized", "Требуется аутентификация"));
    }
}
