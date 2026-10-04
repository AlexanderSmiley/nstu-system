package ru.nstu.system.event.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;
import ru.nstu.system.event.domain.converter.AudienceConverter;

/**
 * A calendar entry on a single day (change add-calendar-module; design.md D2).
 *
 * <p>Backed by {@code event.calendar_entry}. Dates are calendar values without a
 * time zone ({@link LocalDate} + optional {@link LocalTime}), which keeps the
 * "same day, same time" promise independent of the client TZ. The audience is
 * persisted through {@link AudienceConverter}.</p>
 */
@Entity
@Table(name = "calendar_entry")
public class CalendarEntry {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "group_id", nullable = false, updatable = false)
    private UUID groupId;

    @Column(name = "author_account_id", nullable = false, updatable = false)
    private UUID authorAccountId;

    /** Frozen copy of the author's display name; {@code null} when it could not be resolved. */
    @Column(name = "author_display_name")
    private String authorDisplayName;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    @Column(name = "starts_at")
    private LocalTime startsAt;

    @Convert(converter = AudienceConverter.class)
    @Column(name = "audience", nullable = false)
    private Audience audience;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Required by JPA. */
    protected CalendarEntry() {
    }

    /**
     * Creates an entry.
     *
     * @param id                generated identifier
     * @param groupId           owning group (the single default group in the MVP)
     * @param authorAccountId   {@code sub} of the creator's access token
     * @param authorDisplayName frozen author name, or {@code null} when unavailable
     * @param title             non-blank title, already trimmed by the caller
     * @param description       optional free text
     * @param startsOn          the day the entry is placed on
     * @param startsAt          optional time of day
     * @param audience          who may see the entry
     * @param now               creation instant
     */
    public static CalendarEntry create(UUID id,
                                       UUID groupId,
                                       UUID authorAccountId,
                                       String authorDisplayName,
                                       String title,
                                       String description,
                                       LocalDate startsOn,
                                       LocalTime startsAt,
                                       Audience audience,
                                       Instant now) {
        CalendarEntry entry = new CalendarEntry();
        entry.id = Objects.requireNonNull(id, "id");
        entry.groupId = Objects.requireNonNull(groupId, "groupId");
        entry.authorAccountId = Objects.requireNonNull(authorAccountId, "authorAccountId");
        entry.authorDisplayName = authorDisplayName;
        entry.title = Objects.requireNonNull(title, "title");
        entry.description = description;
        entry.startsOn = Objects.requireNonNull(startsOn, "startsOn");
        entry.startsAt = startsAt;
        entry.audience = Objects.requireNonNull(audience, "audience");
        entry.createdAt = Objects.requireNonNull(now, "now");
        entry.updatedAt = now;
        return entry;
    }

    /**
     * Applies a partial edit (change add-preferences-and-calendar-ui; design.md D6).
     *
     * <p>Only the provided (non-{@code null}) values are written, so an omitted
     * field keeps its current value — the caller cannot accidentally blank a field
     * by leaving it out of the PATCH body. The mutable timestamp is always
     * refreshed. The author is never changed.</p>
     *
     * @param title       new non-blank title, already trimmed; {@code null} to keep
     * @param description new description; {@code null} to keep
     * @param startsOn    new day; {@code null} to keep
     * @param startsAt    new time; {@code null} to keep
     * @param audience    new audience; {@code null} to keep
     * @param now         edit instant, stored as {@code updated_at}
     */
    public void update(String title,
                       String description,
                       LocalDate startsOn,
                       LocalTime startsAt,
                       Audience audience,
                       Instant now) {
        if (title != null) {
            this.title = title;
        }
        if (description != null) {
            this.description = description;
        }
        if (startsOn != null) {
            this.startsOn = startsOn;
        }
        if (startsAt != null) {
            this.startsAt = startsAt;
        }
        if (audience != null) {
            this.audience = audience;
        }
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public UUID getId() {
        return id;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public UUID getAuthorAccountId() {
        return authorAccountId;
    }

    public String getAuthorDisplayName() {
        return authorDisplayName;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public LocalDate getStartsOn() {
        return startsOn;
    }

    public LocalTime getStartsAt() {
        return startsAt;
    }

    public Audience getAudience() {
        return audience;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
