package ru.nstu.system.event.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import ru.nstu.system.event.domain.converter.QueueEntryStatusConverter;
import ru.nstu.system.event.domain.converter.QueueOriginConverter;

/**
 * A single queue entry backed by {@code event.queue_entry} (design.md D15, D16,
 * D17; spec "Очередь сдачи").
 *
 * <p>One table serves both the live queue ({@code WAITING}/{@code PAUSED}) and the
 * surrender journal ({@code PASSED}); the journal is simply the passed rows
 * ordered by {@code passed_at}. A passed row is immutable and never deleted, so
 * it never participates in reordering or the entry limit.</p>
 *
 * <p>The name snapshot is captured on joining and never rewritten afterwards
 * (spec "Привязка записи к участнику"), which is why {@code name} is only set by
 * {@link #join(UUID, UUID, String, String, int, UUID, UUID, Instant)}.</p>
 *
 * <p>Mutations are intent-revealing; there are no public setters, so an invalid
 * status transition cannot be expressed.</p>
 */
@Entity
@Table(name = "queue_entry")
public class QueueEntry {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "name", nullable = false)
    private String name;

    /** {@code trim + collapsed spaces + lowercase}; uniqueness is checked on this value. */
    @Column(name = "name_normalized", nullable = false)
    private String nameNormalized;

    @Column(name = "position", nullable = false)
    private int position;

    @Convert(converter = QueueEntryStatusConverter.class)
    @Column(name = "status", nullable = false)
    private QueueEntryStatus status;

    @Column(name = "holder_account_id", updatable = false)
    private UUID holderAccountId;

    @Column(name = "guest_ref", updatable = false)
    private UUID guestRef;

    @Convert(converter = QueueOriginConverter.class)
    @Column(name = "origin", nullable = false, updatable = false)
    private QueueOrigin origin;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "passed_at")
    private Instant passedAt;

    @Column(name = "passed_by")
    private UUID passedBy;

    /** Required by JPA. */
    protected QueueEntry() {
    }

    /**
     * Creates a {@code WAITING} entry at the end of the queue.
     *
     * @param id                 generated identifier
     * @param eventId            owning event
     * @param name               display-name snapshot, already trimmed
     * @param nameNormalized     comparison form of {@code name}
     * @param position           {@code max(active position) + 1}
     * @param holderAccountId    account of a student/staff participant, or {@code null} for a guest
     * @param guestRef           guest session identifier, or {@code null} for an account
     * @param now                creation instant
     */
    public static QueueEntry join(UUID id,
                                  UUID eventId,
                                  String name,
                                  String nameNormalized,
                                  int position,
                                  UUID holderAccountId,
                                  UUID guestRef,
                                  Instant now) {
        return create(id, eventId, name, nameNormalized, position, QueueEntryStatus.WAITING,
                holderAccountId, guestRef, QueueOrigin.JOIN, now, null, null);
    }

    /**
     * Creates an active stub entered by staff on behalf of a participant who has
     * no account and no guest session (spec "Записи, созданные персоналом за
     * участника"; design.md D18): {@code origin = STAFF}, both {@code holderAccountId}
     * and {@code guestRef} are {@code null}, status {@code WAITING}.
     */
    public static QueueEntry staffStub(UUID id,
                                       UUID eventId,
                                       String name,
                                       String nameNormalized,
                                       int position,
                                       Instant now) {
        return create(id, eventId, name, nameNormalized, position, QueueEntryStatus.WAITING,
                null, null, QueueOrigin.STAFF, now, null, null);
    }

    /**
     * Carries an active entry over to another event (spec "Хвост"; design.md D18):
     * {@code origin = CARRY_OVER}. When the source entry was bound to an account it
     * keeps that binding; a guest (or staff-stub) source becomes a placeholder with
     * both {@code holderAccountId} and {@code guestRef} {@code null}.
     *
     * @param holderAccountId the source account, or {@code null} for a guest source
     */
    public static QueueEntry carryOver(UUID id,
                                       UUID eventId,
                                       String name,
                                       String nameNormalized,
                                       int position,
                                       UUID holderAccountId,
                                       Instant now) {
        return create(id, eventId, name, nameNormalized, position, QueueEntryStatus.WAITING,
                holderAccountId, null, QueueOrigin.CARRY_OVER, now, null, null);
    }

    /**
     * Recreates a persisted entry from an archive payload (task 9.6), preserving
     * every column including the historical position and surrender metadata.
     */
    public static QueueEntry restored(UUID id,
                                      UUID eventId,
                                      String name,
                                      String nameNormalized,
                                      int position,
                                      QueueEntryStatus status,
                                      UUID holderAccountId,
                                      UUID guestRef,
                                      QueueOrigin origin,
                                      Instant createdAt,
                                      Instant passedAt,
                                      UUID passedBy) {
        return create(id, eventId, name, nameNormalized, position, status,
                holderAccountId, guestRef, origin, createdAt, passedAt, passedBy);
    }

    private static QueueEntry create(UUID id,
                                     UUID eventId,
                                     String name,
                                     String nameNormalized,
                                     int position,
                                     QueueEntryStatus status,
                                     UUID holderAccountId,
                                     UUID guestRef,
                                     QueueOrigin origin,
                                     Instant createdAt,
                                     Instant passedAt,
                                     UUID passedBy) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(nameNormalized, "nameNormalized");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(createdAt, "createdAt");
        if (position < 1) {
            throw new IllegalArgumentException("position must be >= 1");
        }
        if (holderAccountId != null && guestRef != null) {
            throw new IllegalArgumentException(
                    "holderAccountId and guestRef are mutually exclusive");
        }
        if (origin == QueueOrigin.JOIN && (holderAccountId == null) == (guestRef == null)) {
            throw new IllegalArgumentException(
                    "a JOIN entry needs exactly one of holderAccountId/guestRef");
        }
        if (status == QueueEntryStatus.PASSED && (passedAt == null || passedBy == null)) {
            throw new IllegalArgumentException("a PASSED entry needs passedAt and passedBy");
        }
        QueueEntry entry = new QueueEntry();
        entry.id = id;
        entry.eventId = eventId;
        entry.name = name;
        entry.nameNormalized = nameNormalized;
        entry.position = position;
        entry.status = status;
        entry.holderAccountId = holderAccountId;
        entry.guestRef = guestRef;
        entry.origin = origin;
        entry.createdAt = createdAt;
        entry.passedAt = passedAt;
        entry.passedBy = passedBy;
        return entry;
    }

    /** {@code WAITING -> PAUSED}; the position is intentionally kept. */
    public void pause() {
        requireStatus(QueueEntryStatus.WAITING, "приостановить");
        this.status = QueueEntryStatus.PAUSED;
    }

    /** {@code PAUSED -> WAITING}; the position is intentionally kept. */
    public void resume() {
        requireStatus(QueueEntryStatus.PAUSED, "вернуть");
        this.status = QueueEntryStatus.WAITING;
    }

    /** Marks the entry as surrendered: {@code -> PASSED} with an immutable journal timestamp. */
    public void pass(Instant passedAt, UUID passedBy) {
        requireStatus(QueueEntryStatus.WAITING, "отметить сданной");
        this.status = QueueEntryStatus.PASSED;
        this.passedAt = Objects.requireNonNull(passedAt, "passedAt");
        this.passedBy = Objects.requireNonNull(passedBy, "passedBy");
    }

    /** Moves an active entry to a new position during a renumbering pass. */
    public void moveTo(int newPosition) {
        if (!status.isActive()) {
            throw new IllegalStateException("passed entries cannot be reordered");
        }
        if (newPosition < 1) {
            throw new IllegalArgumentException("position must be >= 1");
        }
        this.position = newPosition;
    }

    private void requireStatus(QueueEntryStatus expected, String action) {
        if (this.status != expected) {
            throw new IllegalStateException("cannot " + action + " entry in status " + this.status);
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getName() {
        return name;
    }

    public String getNameNormalized() {
        return nameNormalized;
    }

    public int getPosition() {
        return position;
    }

    public QueueEntryStatus getStatus() {
        return status;
    }

    public UUID getHolderAccountId() {
        return holderAccountId;
    }

    public UUID getGuestRef() {
        return guestRef;
    }

    public QueueOrigin getOrigin() {
        return origin;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPassedAt() {
        return passedAt;
    }

    public UUID getPassedBy() {
        return passedBy;
    }
}
