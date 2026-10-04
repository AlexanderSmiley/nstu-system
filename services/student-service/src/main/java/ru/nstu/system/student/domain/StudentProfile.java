package ru.nstu.system.student.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Profile of a student or staff account, backed by {@code student.student_profile}
 * (design.md D2, D15).
 *
 * <p>The primary key equals {@code auth.account.id}: there is exactly one profile
 * per non-admin account. Personal data (full name, contacts) lives only in this
 * schema; the email is owned by {@code auth.account} and is deliberately not
 * mapped here.</p>
 *
 * <p>The mapping is validated at startup against the Flyway migration
 * ({@code spring.jpa.hibernate.ddl-auto=validate}).</p>
 */
@Entity
@Table(name = "student_profile")
public class StudentProfile {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    /**
     * The study group is part of the profile identity and cannot be changed by
     * the owner (task 6.1); therefore the column is not updatable through JPA.
     */
    @Column(name = "group_id", nullable = false, updatable = false)
    private UUID groupId;

    /** Open-ended contact set (phone, telegram, ...) stored as {@code jsonb}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "contacts")
    private Map<String, Object> contacts;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Required by JPA. */
    protected StudentProfile() {
    }

    private StudentProfile(UUID id,
                           String fullName,
                           UUID groupId,
                           Map<String, Object> contacts,
                           Instant now) {
        this.id = id;
        this.fullName = fullName;
        this.groupId = groupId;
        this.contacts = copyContacts(contacts);
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Creates a new profile for the given account.
     *
     * @param accountId {@code auth.account.id}
     * @param fullName  display name at creation time
     * @param groupId   owning study group (the single default group in the MVP)
     * @return a new, not-yet-persisted profile
     */
    public static StudentProfile create(UUID accountId, String fullName, UUID groupId) {
        return new StudentProfile(
                Objects.requireNonNull(accountId, "accountId"),
                Objects.requireNonNull(fullName, "fullName"),
                Objects.requireNonNull(groupId, "groupId"),
                null,
                Instant.now());
    }

    /** Replaces the full name and bumps {@code updated_at}. */
    public void changeFullName(String newFullName) {
        this.fullName = Objects.requireNonNull(newFullName, "newFullName");
        touch();
    }

    /**
     * Replaces the contact map. A {@code null} map clears the contacts; callers
     * that want a partial update must read the current value first.
     */
    public void changeContacts(Map<String, Object> newContacts) {
        this.contacts = copyContacts(newContacts);
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    private static Map<String, Object> copyContacts(Map<String, Object> source) {
        return source == null ? null : new LinkedHashMap<>(source);
    }

    public UUID getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public UUID getGroupId() {
        return groupId;
    }

    /**
     * @return an unmodifiable view of the contacts, or {@code null} when none are
     *         stored; mutation goes through {@link #changeContacts(Map)}
     */
    public Map<String, Object> getContacts() {
        return contacts == null ? null : Collections.unmodifiableMap(contacts);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
