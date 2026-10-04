package ru.nstu.system.event.web.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import ru.nstu.system.event.domain.Audience;

/**
 * Body of {@code PATCH /api/calendar/{id}}
 * (change add-preferences-and-calendar-ui; design.md D6).
 *
 * <p>Partial edit: every field is optional and a {@code null}/absent value means
 * "keep the current one". The rules for a provided field are exactly the rules of
 * creation — a non-blank {@code title} of at most 200 characters, a
 * {@code startsOn} inside the displayed window when {@code from}/{@code to} are
 * sent, and an {@code audience} the caller's role may target.</p>
 *
 * <p>{@code from}/{@code to} carry the calendar window currently displayed by the
 * client, so the server can apply the same "date inside the displayed window"
 * check as {@code POST /api/calendar}. They are only consulted when a new
 * {@code startsOn} is supplied.</p>
 *
 * <p>Because an absent field and an explicit JSON {@code null} are
 * indistinguishable here, this endpoint cannot clear {@code description} or
 * {@code startsAt}; it only replaces them with provided values. Clearing is not
 * part of the spec ("изменить название, описание, дату, время и адресат").</p>
 *
 * @param title       optional new name
 * @param description optional new free text
 * @param startsOn    optional new day
 * @param startsAt    optional new time of day
 * @param audience    optional new {@code ME}/{@code GROUP}/{@code STAFF}
 * @param from        optional window start (displayed calendar)
 * @param to          optional window end (displayed calendar)
 */
public record UpdateCalendarEntryRequest(
        String title,
        String description,
        LocalDate startsOn,
        LocalTime startsAt,
        Audience audience,
        LocalDate from,
        LocalDate to) {
}
