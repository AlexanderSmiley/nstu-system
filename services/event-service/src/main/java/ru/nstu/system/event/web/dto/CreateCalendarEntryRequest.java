package ru.nstu.system.event.web.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import ru.nstu.system.event.domain.Audience;

/**
 * Body of {@code POST /api/calendar} (change add-calendar-module; design.md D4).
 *
 * <p>{@code title} and {@code startsOn} are required; {@code description} and
 * {@code startsAt} are optional; a missing {@code audience} defaults to
 * {@link Audience#ME} in the service.</p>
 *
 * <p>{@code from}/{@code to} carry the two-week window currently displayed by the
 * client. They are optional on the wire, but when present the service rejects a
 * {@code startsOn} outside of them with {@code invalid_date}, which is what the
 * spec asks the server to enforce ("дата вне отображаемого окна отклоняется").
 * Without them a direct caller can only be checked for a well-formed, non-null
 * date — the server has no notion of "displayed" otherwise.</p>
 *
 * @param title       entry name, required
 * @param description optional free text
 * @param startsOn    the day the entry is placed on, required
 * @param startsAt    optional time of day
 * @param audience    {@code ME}/{@code GROUP}/{@code STAFF}; default {@code ME}
 * @param from        optional window start (displayed calendar)
 * @param to          optional window end (displayed calendar)
 */
public record CreateCalendarEntryRequest(
        String title,
        String description,
        LocalDate startsOn,
        LocalTime startsAt,
        Audience audience,
        LocalDate from,
        LocalDate to) {
}
