package ru.nstu.system.event.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import ru.nstu.system.event.domain.converter.AvailabilityConverter;
import ru.nstu.system.event.domain.converter.EntryUnitConverter;
import ru.nstu.system.event.domain.converter.EventStatusConverter;
import ru.nstu.system.event.domain.converter.EventTypeConverter;
import ru.nstu.system.event.domain.converter.JournalVisibilityConverter;

/**
 * Queue event backed by {@code event.event} (design.md D15).
 *
 * <p>The mapping covers every column of {@code event.event}, including the archive
 * pair {@code archived_at}/{@code archive_payload}, and is verified at startup
 * against the Flyway migration ({@code spring.jpa.hibernate.ddl-auto=validate}).</p>
 *
 * <p>Mutators are intent-revealing and bump {@code updated_at}; there are no
 * public setters, so a status can only change through {@link #close(Instant)} /
 * {@link #open()} / {@link #archive(byte[], Instant)} / {@link #restore()}, which
 * keeps business rules out of the controllers.</p>
 */
@Entity
@Table(name = "event")
public class Event {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "group_id", nullable = false, updatable = false)
    private UUID groupId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description")
    private String description;

    /** Always {@code QUEUE} in the MVP; fixed at creation. */
    @Convert(converter = EventTypeConverter.class)
    @Column(name = "type", nullable = false, updatable = false)
    private EventType type;

    @Convert(converter = AvailabilityConverter.class)
    @Column(name = "availability", nullable = false)
    private Availability availability;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "entry_limit", nullable = false)
    private int entryLimit;

    @Convert(converter = EntryUnitConverter.class)
    @Column(name = "entry_unit", nullable = false)
    private EntryUnit entryUnit;

    @Convert(converter = JournalVisibilityConverter.class)
    @Column(name = "journal_visibility", nullable = false)
    private JournalVisibility journalVisibility;

    @Column(name = "retention_days", nullable = false)
    private int retentionDays;

    @Column(name = "slug", nullable = false, unique = true)
    private String slug;

    @Convert(converter = EventStatusConverter.class)
    @Column(name = "status", nullable = false)
    private EventStatus status;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    /** {@code gzip(jsonb)} snapshot of the queue and journal; set only while {@code ARCHIVED}. */
    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "archive_payload")
    private byte[] archivePayload;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Required by JPA. */
    protected Event() {
    }

    /**
     * Creates a new {@code OPEN} queue event.
     *
     * @param id                generated identifier
     * @param groupId           owning group (the single default group in the MVP)
     * @param title             non-blank title, already trimmed by the caller
     * @param description       optional free text
     * @param availability      access level
     * @param startsAt          optional start time
     * @param entryLimit        positive maximum number of active entries
     * @param entryUnit         label of a single entry
     * @param journalVisibility who may read the surrender journal
     * @param retentionDays     days the event is kept after closing (>= 1)
     * @param slug              unique short-link slug
     * @param createdBy         {@code sub} of the creator's access token
     * @param now               creation instant
     */
    public static Event create(UUID id,
                               UUID groupId,
                               String title,
                               String description,
                               Availability availability,
                               Instant startsAt,
                               int entryLimit,
                               EntryUnit entryUnit,
                               JournalVisibility journalVisibility,
                               int retentionDays,
                               String slug,
                               UUID createdBy,
                               Instant now) {
        Event event = new Event();
        event.id = Objects.requireNonNull(id, "id");
        event.groupId = Objects.requireNonNull(groupId, "groupId");
        event.title = Objects.requireNonNull(title, "title");
        event.description = description;
        event.type = EventType.QUEUE;
        event.availability = Objects.requireNonNull(availability, "availability");
        event.startsAt = startsAt;
        event.entryLimit = entryLimit;
        event.entryUnit = Objects.requireNonNull(entryUnit, "entryUnit");
        event.journalVisibility = Objects.requireNonNull(journalVisibility, "journalVisibility");
        event.retentionDays = retentionDays;
        event.slug = Objects.requireNonNull(slug, "slug");
        event.status = EventStatus.OPEN;
        event.closedAt = null;
        event.createdBy = Objects.requireNonNull(createdBy, "createdBy");
        event.createdAt = Objects.requireNonNull(now, "now");
        event.updatedAt = now;
        return event;
    }

    /** Replaces the title (already validated and trimmed by the caller). */
    public void changeTitle(String newTitle) {
        this.title = Objects.requireNonNull(newTitle, "newTitle");
        touch();
    }

    /** Replaces the description; {@code null} clears it. */
    public void changeDescription(String newDescription) {
        this.description = newDescription;
        touch();
    }

    public void changeAvailability(Availability newAvailability) {
        this.availability = Objects.requireNonNull(newAvailability, "newAvailability");
        touch();
    }

    /** Replaces the start time; {@code null} removes it. */
    public void changeStartsAt(Instant newStartsAt) {
        this.startsAt = newStartsAt;
        touch();
    }

    /**
     * Replaces the entry limit. Lowering it below the number of active entries is
     * allowed: existing entries are never removed.
     */
    public void changeEntryLimit(int newEntryLimit) {
        this.entryLimit = newEntryLimit;
        touch();
    }

    /** Replaces the entry unit; only legal while no active entries exist. */
    public void changeEntryUnit(EntryUnit newEntryUnit) {
        this.entryUnit = Objects.requireNonNull(newEntryUnit, "newEntryUnit");
        touch();
    }

    public void changeJournalVisibility(JournalVisibility newVisibility) {
        this.journalVisibility = Objects.requireNonNull(newVisibility, "newVisibility");
        touch();
    }

    public void changeRetentionDays(int newRetentionDays) {
        this.retentionDays = newRetentionDays;
        touch();
    }

    public void changeSlug(String newSlug) {
        this.slug = Objects.requireNonNull(newSlug, "newSlug");
        touch();
    }

    /** Closes the event and starts the retention countdown. */
    public void close(Instant now) {
        this.status = EventStatus.CLOSED;
        this.closedAt = Objects.requireNonNull(now, "now");
        touch();
    }

    /** Re-opens a closed event and stops the retention countdown. */
    public void open() {
        this.status = EventStatus.OPEN;
        this.closedAt = null;
        touch();
    }

    /**
     * Moves a {@code CLOSED} event to {@code ARCHIVED}, storing the gzip-compressed
     * snapshot of its queue and journal (design.md D19). Only a closed event can be
     * archived; the caller is responsible for deleting the live {@code queue_entry}
     * rows in the same transaction.
     *
     * @param payload gzip-compressed JSON snapshot, never {@code null}
     * @param now     archive instant
     */
    public void archive(byte[] payload, Instant now) {
        if (status != EventStatus.CLOSED) {
            throw new IllegalStateException("only a CLOSED event can be archived, was " + status);
        }
        this.archivePayload = Objects.requireNonNull(payload, "payload");
        this.archivedAt = Objects.requireNonNull(now, "now");
        this.status = EventStatus.ARCHIVED;
        touch();
    }

    /**
     * Returns an {@code ARCHIVED} event to {@code CLOSED} after its queue rows have
     * been recreated from {@code archive_payload} (task 9.6). {@code closed_at} is
     * kept so the original retention window is not silently extended.
     */
    public void restore() {
        if (status != EventStatus.ARCHIVED) {
            throw new IllegalStateException("only an ARCHIVED event can be restored, was " + status);
        }
        this.archivePayload = null;
        this.archivedAt = null;
        this.status = EventStatus.CLOSED;
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public EventType getType() {
        return type;
    }

    public Availability getAvailability() {
        return availability;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public int getEntryLimit() {
        return entryLimit;
    }

    public EntryUnit getEntryUnit() {
        return entryUnit;
    }

    public JournalVisibility getJournalVisibility() {
        return journalVisibility;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public String getSlug() {
        return slug;
    }

    public EventStatus getStatus() {
        return status;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    /** @return a defensive copy of the compressed snapshot, or {@code null} when not archived */
    public byte[] getArchivePayload() {
        return archivePayload == null ? null : archivePayload.clone();
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
