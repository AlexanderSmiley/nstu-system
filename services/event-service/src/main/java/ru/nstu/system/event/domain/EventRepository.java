package ru.nstu.system.event.domain;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository over {@code event.event}.
 *
 * <p>The visibility query already filters by status, group and access level, so
 * the service only has to decide which levels a role may see; this keeps the
 * "visible events" rule in one place (task 7.7).</p>
 */
public interface EventRepository extends JpaRepository<Event, UUID> {

    /**
     * Loads an event under a pessimistic row lock ({@code SELECT ... FOR UPDATE},
     * design.md D16). Every queue mutation begins with this call, so concurrent
     * joins/advances/pauses for the same event are serialised on the event row.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Event e where e.id = :id")
    Optional<Event> findByIdForUpdate(@Param("id") UUID id);

    /** Looks an event up by its public short-link slug. */
    Optional<Event> findBySlug(String slug);

    /** Collision check used while minting a unique slug. */
    boolean existsBySlug(String slug);

    /**
     * Active-list query: only {@code OPEN} events of the given group whose
     * availability is among {@code availabilities}, newest first.
     */
    List<Event> findByStatusAndGroupIdAndAvailabilityInOrderByCreatedAtDesc(
            EventStatus status, UUID groupId, Collection<Availability> availabilities);

    /**
     * History query (task 9.7): events of the given group in any of
     * {@code statuses}, most recently closed first. Archival states are never
     * filtered by availability — every event of the group is visible to staff and
     * administrators, who are the only roles the history is built for.
     */
    List<Event> findByStatusInAndGroupIdOrderByClosedAtDesc(
            Collection<EventStatus> statuses, UUID groupId);

    /**
     * Retention sweep selection (task 9.5; design.md D19): {@code CLOSED} events
     * whose {@code closed_at + retention_days} lies in the past. Rows are locked
     * with {@code FOR UPDATE SKIP LOCKED}, so two sweeps never archive the same
     * event, and {@code ARCHIVED} rows are excluded by the {@code status} filter
     * (hence never reprocessed).
     *
     * <p>Native because the date arithmetic and the lock clause are not
     * expressible in JPQL; a native query returning {@code event.*} maps straight
     * back to the {@link Event} entity.</p>
     *
     * @param limit maximum number of events claimed per sweep
     */
    @Query(value = "select * from event.event "
            + "where status = 'CLOSED' "
            + "and closed_at is not null "
            + "and closed_at + (retention_days * interval '1 day') < now() "
            + "order by closed_at "
            + "limit :limit "
            + "for update skip locked", nativeQuery = true)
    List<Event> findDueForArchiveForUpdateSkipLocked(@Param("limit") int limit);
}
