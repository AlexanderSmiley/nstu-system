package ru.nstu.system.event.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository over {@code event.queue_entry} (design.md D15, D16, D17).
 *
 * <p>Every mutating caller takes a pessimistic lock on the owning event row
 * first ({@link EventRepository#findByIdForUpdate(UUID)}), so the queries here do
 * not need locking hints themselves: holding the event lock serialises all queue
 * changes for an event. The partial unique indexes serve as the last line of
 * defence (design.md D16).</p>
 *
 * <p>The two bulk updates are the renumbering primitive used by reordering: all
 * active positions are first shifted by a large constant (which preserves their
 * mutual uniqueness), then stamped with their final {@code 1..K} values. Doing it
 * in one pass would violate the partial unique index mid-statement.</p>
 */
public interface QueueEntryRepository extends JpaRepository<QueueEntry, UUID> {

    /** Entry lookup scoped to its event; an entry of another event is a 404. */
    Optional<QueueEntry> findByIdAndEventId(UUID id, UUID eventId);

    Optional<QueueEntry> findFirstByEventIdAndStatusOrderByPositionAsc(
            UUID eventId, QueueEntryStatus status);

    Optional<QueueEntry> findFirstByEventIdAndStatusInOrderByPositionDesc(
            UUID eventId, Collection<QueueEntryStatus> statuses);

    List<QueueEntry> findByEventIdAndStatusInOrderByPositionAsc(
            UUID eventId, Collection<QueueEntryStatus> statuses);

    /** All rows of an event (active and passed) in position order; used by archival (task 9.4). */
    List<QueueEntry> findByEventIdOrderByPositionAsc(UUID eventId);

    /** Journal query: stable order by surrender time, tie-broken by id for deterministic ETags. */
    List<QueueEntry> findByEventIdAndStatusOrderByPassedAtAscIdAsc(
            UUID eventId, QueueEntryStatus status);

    long countByEventIdAndStatusIn(UUID eventId, Collection<QueueEntryStatus> statuses);

    boolean existsByEventIdAndNameNormalizedAndStatusIn(
            UUID eventId, String nameNormalized, Collection<QueueEntryStatus> statuses);

    boolean existsByEventIdAndHolderAccountIdAndStatusIn(
            UUID eventId, UUID holderAccountId, Collection<QueueEntryStatus> statuses);

    boolean existsByEventIdAndGuestRefAndStatusIn(
            UUID eventId, UUID guestRef, Collection<QueueEntryStatus> statuses);

    /** Shifts every active position by {@code offset}, preserving relative order. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update QueueEntry e set e.position = e.position + :offset "
            + "where e.eventId = :eventId and e.status in :statuses")
    int shiftActivePositions(@Param("eventId") UUID eventId,
                             @Param("statuses") Collection<QueueEntryStatus> statuses,
                             @Param("offset") int offset);

    /** Stamps a single entry with its final position during a renumbering pass. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update QueueEntry e set e.position = :position where e.id = :id")
    int updatePosition(@Param("id") UUID id, @Param("position") int position);

    /**
     * Deletes every row of an event after its snapshot has been stored in
     * {@code event.archive_payload} (task 9.4).
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from QueueEntry e where e.eventId = :eventId")
    int deleteByEventId(@Param("eventId") UUID eventId);
}
