package ru.nstu.system.student.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import ru.nstu.system.contracts.Groups;
import ru.nstu.system.contracts.events.AccountCreatedPayload;
import ru.nstu.system.contracts.events.DomainEvent;
import ru.nstu.system.contracts.events.EventTypes;
import ru.nstu.system.contracts.events.ProfileUpdatedPayload;
import ru.nstu.system.contracts.outbox.OutboxWriter;
import ru.nstu.system.student.domain.StudentProfile;
import ru.nstu.system.student.domain.StudentProfileRepository;
import ru.nstu.system.student.error.ApiException;

/**
 * Profile lifecycle: creation from {@code account.created} and self-service edits
 * (design.md D2, tasks 6.1/6.4).
 *
 * <p>Writing a profile and appending {@code profile.updated} to the outbox happen
 * inside one transaction, which is what makes the outbox pattern correct: the
 * event is either committed together with the change or not at all.</p>
 */
@Service
public class StudentProfileService {

    private static final Logger log = LoggerFactory.getLogger(StudentProfileService.class);

    /** Name of the primary-key constraint of {@code student.student_profile}. */
    private static final String PROFILE_PK_CONSTRAINT = "student_profile_pk";

    /** Upper bound of {@code student_profile.full_name}. */
    private static final int FULL_NAME_MAX_LENGTH = 255;

    /**
     * Neutral name used when a profile is created lazily for an account whose
     * original {@code account.created} event predates this change and carries no
     * display name. {@code full_name} is {@code NOT NULL}, so a value is required.
     */
    private static final String NEUTRAL_FULL_NAME = "Пользователь";

    /**
     * Insert-once profile provisioning used by lazy creation. The primary key is
     * the account id, so a concurrent insert is absorbed rather than failing.
     */
    private static final String INSERT_PROFILE_IF_ABSENT =
            "insert into student.student_profile (id, full_name, group_id) values (?, ?, ?) "
                    + "on conflict (id) do nothing";

    /**
     * Read-only lookup of a study group name. Kept as a plain JDBC query because
     * the directory is a trivial, single-column read and does not warrant a full
     * JPA entity/repository pair (design.md D7).
     */
    private static final String SELECT_GROUP_NAME = "select name from student.app_group where id = ?";

    private final StudentProfileRepository repository;

    private final OutboxWriter outboxWriter;

    private final JdbcTemplate jdbcTemplate;

    public StudentProfileService(StudentProfileRepository repository,
                                 OutboxWriter outboxWriter,
                                 JdbcTemplate jdbcTemplate) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.outboxWriter = Objects.requireNonNull(outboxWriter, "outboxWriter");
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    /**
     * Creates the profile for a newly created account, regardless of role —
     * including {@code ADMIN} (change add-preferences-and-calendar-ui, design.md
     * D1; identity spec "Профиль у всех аккаунтов, включая администратора").
     *
     * <p>The operation is idempotent at two levels: by {@code eventId} in the
     * listener and, defensively here, by the account id (primary key), so a
     * duplicate event can never produce a second profile.</p>
     *
     * @param payload decoded {@code account.created} body
     */
    @Transactional
    public void createProfileFromAccount(AccountCreatedPayload payload) {
        Objects.requireNonNull(payload, "payload");
        UUID accountId = Objects.requireNonNull(payload.accountId(), "accountId");
        String role = payload.role();
        if (repository.existsById(accountId)) {
            log.debug("Profile for account {} already exists; skipping creation", accountId);
            return;
        }

        String fullName = resolveFullName(payload);
        try {
            repository.saveAndFlush(StudentProfile.create(accountId, fullName, Groups.DEFAULT_GROUP_ID));
        } catch (DataIntegrityViolationException ex) {
            if (isProfilePkViolation(ex)) {
                // Concurrent/duplicate creation; the account id keeps the invariant.
                log.warn("Profile for account {} was created concurrently; ignoring duplicate", accountId);
                return;
            }
            // Any other integrity failure (e.g. a missing group row) is transient:
            // rethrow so the listener dead-letters the message and the DLQ
            // re-drive scheduler retries it later.
            throw ex;
        }
        log.info("Created student profile for account {} (role {})", accountId, role);
    }

    /** @return the profile of the account, or empty when none exists */
    @Transactional(readOnly = true)
    public Optional<StudentProfile> findProfile(UUID accountId) {
        return repository.findById(Objects.requireNonNull(accountId, "accountId"));
    }

    /**
     * Returns the account's profile, creating it lazily when missing
     * (change add-preferences-and-calendar-ui, design.md D1; task 1.2).
     *
     * <p>Accounts created before the "profile for every account" rule — notably
     * administrators — have no profile until their first self-service call. The
     * insert is race-safe ({@code on conflict (id) do nothing}) and followed by a
     * re-read, so two concurrent first calls converge on the same row. The name
     * falls back to {@link #NEUTRAL_FULL_NAME} because {@code full_name} is
     * {@code NOT NULL}.</p>
     *
     * @param accountId {@code auth.account.id}, also the profile primary key
     * @return the existing or freshly created profile
     */
    @Transactional
    public StudentProfile getOrCreateProfile(UUID accountId) {
        Objects.requireNonNull(accountId, "accountId");
        Optional<StudentProfile> existing = repository.findById(accountId);
        if (existing.isPresent()) {
            return existing.get();
        }
        jdbcTemplate.update(INSERT_PROFILE_IF_ABSENT, accountId, NEUTRAL_FULL_NAME, Groups.DEFAULT_GROUP_ID);
        return repository.findById(accountId)
                .orElseThrow(() -> new IllegalStateException(
                        "student.student_profile row for " + accountId + " is missing after an upsert"));
    }

    /**
     * Resolves the human-readable name of a study group from the directory.
     *
     * <p>The profile API is never failed by a missing directory entry: an unknown
     * group simply yields an empty result and the caller omits the name (design.md
     * D7, identity spec "Название группы в ответе профиля").</p>
     *
     * @param groupId {@code student.app_group.id} carried by a profile
     * @return the group name, or empty when the group is not in the directory
     */
    @Transactional(readOnly = true)
    public Optional<String> findGroupName(UUID groupId) {
        Objects.requireNonNull(groupId, "groupId");
        List<String> names = jdbcTemplate.queryForList(SELECT_GROUP_NAME, String.class, groupId);
        return names.isEmpty() ? Optional.empty() : Optional.ofNullable(names.get(0));
    }

    /**
     * Updates the caller's own full name and/or contacts (task 6.1).
     *
     * <p>{@code groupId} is not part of the request and cannot be changed here.
     * When the full name actually changes, a {@code profile.updated} event is
     * appended to the outbox <em>in the same transaction</em> as the update
     * (task 6.4). A contacts-only edit emits no event.</p>
     *
     * <p>An account without a profile gets one lazily first, so editing is
     * available to every authenticated account (change
     * add-preferences-and-calendar-ui, identity spec "Профиль у всех аккаунтов,
     * включая администратора").</p>
     *
     * @param newFullName new full name, or {@code null} to leave it unchanged
     * @param newContacts new contacts, or {@code null} to leave them unchanged
     * @throws ApiException 400 on invalid input
     */
    @Transactional
    public StudentProfile updateOwnProfile(UUID accountId, String newFullName, Map<String, Object> newContacts) {
        Objects.requireNonNull(accountId, "accountId");
        StudentProfile profile = getOrCreateProfile(accountId);

        boolean fullNameChanged = false;
        if (newFullName != null) {
            String normalized = newFullName.trim();
            if (normalized.isEmpty()) {
                throw ApiException.badRequest("invalid_full_name", "ФИО не может быть пустым");
            }
            if (normalized.length() > FULL_NAME_MAX_LENGTH) {
                throw ApiException.badRequest("invalid_full_name",
                        "ФИО не должно превышать " + FULL_NAME_MAX_LENGTH + " символов");
            }
            if (!normalized.equals(profile.getFullName())) {
                profile.changeFullName(normalized);
                fullNameChanged = true;
            }
        }
        if (newContacts != null) {
            profile.changeContacts(newContacts);
        }
        repository.save(profile);

        if (fullNameChanged) {
            outboxWriter.write(DomainEvent.of(
                    EventTypes.PROFILE_UPDATED,
                    new ProfileUpdatedPayload(accountId, profile.getFullName())));
        }
        return profile;
    }

    private static String resolveFullName(AccountCreatedPayload payload) {
        if (StringUtils.hasText(payload.displayName())) {
            return payload.displayName().trim();
        }
        if (StringUtils.hasText(payload.username())) {
            return payload.username().trim();
        }
        throw new IllegalArgumentException(
                "account.created for " + payload.accountId() + " carries neither displayName nor username");
    }

    private static boolean isProfilePkViolation(DataIntegrityViolationException ex) {
        String message = ex.getMostSpecificCause().getMessage();
        return message != null && message.contains(PROFILE_PK_CONSTRAINT);
    }
}
