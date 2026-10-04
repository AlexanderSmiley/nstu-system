package ru.nstu.system.event.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository over {@code event.calendar_entry}
 * (change add-calendar-module; design.md D3).
 *
 * <p>The visibility rule is applied <em>inside</em> the SQL query — an entry is
 * returned when the viewer is its author or when its audience is among the
 * audiences visible to the viewer's role — so personal entries never reach a
 * response only to be filtered out afterwards. Ordering matches the grid: by day,
 * then by time with the timeless entries last, then by title.</p>
 */
public interface CalendarEntryRepository extends JpaRepository<CalendarEntry, UUID> {

    /**
     * @param groupId   owning group
     * @param from      inclusive window start
     * @param to        inclusive window end
     * @param viewerId  account id of the viewer (may be {@code null} for guest)
     * @param audiences audiences the viewer's role may see (never empty)
     */
    @Query("select c from CalendarEntry c "
            + "where c.groupId = :groupId "
            + "and c.startsOn between :from and :to "
            + "and (c.authorAccountId = :viewerId or c.audience in :audiences) "
            + "order by c.startsOn asc, c.startsAt asc nulls last, c.title asc")
    List<CalendarEntry> findVisible(
            @Param("groupId") UUID groupId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("viewerId") UUID viewerId,
            @Param("audiences") Collection<Audience> audiences);
}
