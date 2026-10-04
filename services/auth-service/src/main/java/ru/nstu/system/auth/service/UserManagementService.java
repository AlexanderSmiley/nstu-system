package ru.nstu.system.auth.service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.nstu.system.auth.domain.Account;
import ru.nstu.system.auth.domain.AccountRepository;
import ru.nstu.system.auth.domain.Role;
import ru.nstu.system.auth.web.ApiException;
import ru.nstu.system.auth.web.dto.CreateUserRequest;
import ru.nstu.system.auth.web.dto.CreatedUserResponse;
import ru.nstu.system.auth.web.dto.PasswordResetResponse;
import ru.nstu.system.auth.web.dto.UpdateUserRequest;
import ru.nstu.system.auth.web.dto.UserResponse;
import ru.nstu.system.contracts.events.AccountBlockedPayload;
import ru.nstu.system.contracts.events.AccountCreatedPayload;
import ru.nstu.system.contracts.events.AccountPasswordResetPayload;
import ru.nstu.system.contracts.events.AccountUnblockedPayload;
import ru.nstu.system.contracts.events.AccountUpdatedPayload;
import ru.nstu.system.contracts.events.DomainEvent;
import ru.nstu.system.contracts.events.EventTypes;
import ru.nstu.system.contracts.outbox.OutboxWriter;

/**
 * Administrator-facing account management (identity spec "Создание пользователя
 * администратором", "Редактирование данных пользователя", "Просмотр списка
 * пользователей"; tasks 5.8/5.9).
 *
 * <p>All state changes happen in one transaction together with the matching
 * outbox row (design.md D13): either the account mutation and the domain event are
 * both committed, or neither is. No synchronous call is ever made to
 * {@code student-service}; the profile is created later from the
 * {@code account.created} event (design.md D2/D12).</p>
 *
 * <p>Passwords are generated here, hashed with BCrypt and returned exactly once in
 * the creation response. They are never logged and never stored in clear text.</p>
 */
@Service
public class UserManagementService {

    /** Unique constraints on the account username (raw and normalized). */
    private static final String USERNAME_CONSTRAINT = "account_username_uniq";
    private static final String USERNAME_NORMALIZED_CONSTRAINT = "account_username_normalized_uniq";

    /** Partial unique index enforcing the single-ADMIN invariant. */
    private static final String SINGLE_ADMIN_CONSTRAINT = "account_single_admin_uniq";

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordGenerator passwordGenerator;
    private final UserAdminPolicy userAdminPolicy;
    private final RefreshTokenRevoker refreshTokenRevoker;
    private final OutboxWriter outboxWriter;

    public UserManagementService(AccountRepository accountRepository,
                                 PasswordEncoder passwordEncoder,
                                 PasswordGenerator passwordGenerator,
                                 UserAdminPolicy userAdminPolicy,
                                 RefreshTokenRevoker refreshTokenRevoker,
                                 OutboxWriter outboxWriter) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordGenerator = passwordGenerator;
        this.userAdminPolicy = userAdminPolicy;
        this.refreshTokenRevoker = refreshTokenRevoker;
        this.outboxWriter = outboxWriter;
    }

    /**
     * Creates a {@code STAFF} or {@code STUDENT} account with a generated temporary
     * password and publishes {@code account.created}.
     *
     * @param request creation payload
     * @return the created account plus the one-time temporary password
     * @throws ApiException 400 for an unassignable role or invalid username,
     *                      409 for a duplicate username
     */
    @Transactional
    public CreatedUserResponse create(CreateUserRequest request) {
        Role role = userAdminPolicy.requireAssignableRole(request.role());

        String username = request.username().trim();
        String normalized = normalizeUsername(username);
        if (accountRepository.findByUsernameNormalized(normalized).isPresent()) {
            throw usernameTaken();
        }

        String temporaryPassword = passwordGenerator.generate();
        Account account = Account.create(
                username,
                normalized,
                passwordEncoder.encode(temporaryPassword),
                trimToNull(request.displayName()),
                trimToNull(request.email()),
                role,
                false,
                true);

        try {
            accountRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException ex) {
            throw translateIntegrityViolation(ex);
        }

        outboxWriter.write(DomainEvent.of(
                EventTypes.ACCOUNT_CREATED,
                new AccountCreatedPayload(
                        account.getId(), account.getUsername(), role.name(), account.getDisplayName())));

        return new CreatedUserResponse(
                account.getId(),
                account.getUsername(),
                account.getDisplayName(),
                account.getEmail(),
                account.getRole().name(),
                account.isMustChangePassword(),
                temporaryPassword,
                account.getCreatedAt());
    }

    /**
     * @return every account, oldest first; no password material is included
     */
    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return accountRepository.findAll().stream()
                .sorted((left, right) -> left.getCreatedAt().compareTo(right.getCreatedAt()))
                .map(UserManagementService::toResponse)
                .toList();
    }

    /**
     * Updates the mutable attributes of an account and publishes
     * {@code account.updated}.
     *
     * @param actorId  administrator performing the change
     * @param targetId account being changed
     * @param request  optional new display name, email and role
     * @return the updated account
     * @throws ApiException 400 for an empty or forbidden change, 404 for an unknown account
     */
    @Transactional
    public UserResponse update(UUID actorId, UUID targetId, UpdateUserRequest request) {
        if (request.displayName() == null && request.email() == null && request.role() == null) {
            throw ApiException.badRequest("invalid_request", "Не указаны поля для изменения");
        }

        Account target = accountRepository.findById(targetId)
                .orElseThrow(() -> ApiException.notFound("user_not_found", "Пользователь не найден"));

        boolean changed = false;

        String newDisplayName = target.getDisplayName();
        if (request.displayName() != null) {
            String candidate = trimToNull(request.displayName());
            if (candidate == null) {
                throw ApiException.badRequest("invalid_request", "Отображаемое имя не может быть пустым");
            }
            changed |= !Objects.equals(candidate, newDisplayName);
            newDisplayName = candidate;
        }

        String newEmail = target.getEmail();
        if (request.email() != null) {
            String candidate = trimToNull(request.email());
            changed |= !Objects.equals(candidate, newEmail);
            newEmail = candidate;
        }

        Role newRole = target.getRole();
        if (request.role() != null) {
            Role candidate = userAdminPolicy.requireAssignableRole(request.role());
            userAdminPolicy.ensureRoleChangeAllowed(actorId, target, candidate);
            changed |= candidate != newRole;
            newRole = candidate;
        }

        if (!changed) {
            return toResponse(target);
        }

        target.updateDetails(newDisplayName, newEmail, newRole);
        accountRepository.save(target);

        outboxWriter.write(DomainEvent.of(
                EventTypes.ACCOUNT_UPDATED,
                new AccountUpdatedPayload(
                        target.getId(), target.getUsername(), newRole.name(), newDisplayName)));

        return toResponse(target);
    }

    /**
     * Blocks an account, revokes its active refresh tokens and publishes
     * {@code account.blocked} (identity spec "Блокировка и разблокировка
     * пользователя", task 5.10).
     *
     * <p>The ban takes effect immediately: {@code login} answers 403 for a blocked
     * account and {@code refresh} answers 401 because every token of the account has
     * been revoked. The account row, the revocation and the outbox row are committed
     * together (design.md D13).</p>
     *
     * <p>Blocking is idempotent: blocking an already blocked account is a no-op that
     * still answers 200 and writes no second event. This makes the endpoint safe to
     * retry (a lost response, a repeated click) without duplicating downstream
     * effects and without inventing a spurious conflict.</p>
     *
     * @param actorId  administrator performing the block
     * @param targetId account being blocked
     * @return the blocked account
     * @throws ApiException 400 for self-block or blocking the last administrator,
     *                      404 for an unknown account
     */
    @Transactional
    public UserResponse block(UUID actorId, UUID targetId) {
        Account target = requireAccount(targetId);
        userAdminPolicy.ensureBlockAllowed(actorId, target);
        if (target.isBlocked()) {
            return toResponse(target);
        }

        target.block();
        accountRepository.save(target);
        refreshTokenRevoker.revokeAllActive(target.getId());
        outboxWriter.write(DomainEvent.of(
                EventTypes.ACCOUNT_BLOCKED, new AccountBlockedPayload(target.getId())));
        return toResponse(target);
    }

    /**
     * Lifts a block and publishes {@code account.unblocked} (identity spec
     * "Блокировка и разблокировка пользователя", task 5.10).
     *
     * <p>Unblocking never needs an administrator-safety guard: it can only restore
     * access. It is idempotent for the same reason as {@link #block(UUID, UUID)} — a
     * repeated request is a 200 no-op and writes no second event. The account keeps
     * its previous password; no refresh token is re-issued, so the user logs in
     * again.</p>
     *
     * @param targetId account being unblocked
     * @return the unblocked account
     * @throws ApiException 404 for an unknown account
     */
    @Transactional
    public UserResponse unblock(UUID targetId) {
        Account target = requireAccount(targetId);
        if (!target.isBlocked()) {
            return toResponse(target);
        }

        target.unblock();
        accountRepository.save(target);
        outboxWriter.write(DomainEvent.of(
                EventTypes.ACCOUNT_UNBLOCKED, new AccountUnblockedPayload(target.getId())));
        return toResponse(target);
    }

    /**
     * Generates a new temporary password for an account, revokes its sessions and
     * publishes {@code account.password_reset} (identity spec "Сброс пароля
     * администратором", task 5.10).
     *
     * <p>Only the BCrypt hash is persisted; the clear-text password is returned
     * exactly once in the response and is never logged or stored. The mandatory
     * password change is re-armed so the user must pick a new password at the next
     * login. The account row, the revocation and the outbox row are committed
     * together (design.md D13).</p>
     *
     * @param targetId account whose password is reset
     * @return the account id plus the one-time temporary password
     * @throws ApiException 404 for an unknown account
     */
    @Transactional
    public PasswordResetResponse resetPassword(UUID targetId) {
        Account target = requireAccount(targetId);

        String temporaryPassword = passwordGenerator.generate();
        target.resetPassword(passwordEncoder.encode(temporaryPassword));
        accountRepository.save(target);
        refreshTokenRevoker.revokeAllActive(target.getId());
        outboxWriter.write(DomainEvent.of(
                EventTypes.ACCOUNT_PASSWORD_RESET,
                new AccountPasswordResetPayload(target.getId())));

        return new PasswordResetResponse(
                target.getId(), target.getUsername(), temporaryPassword, target.isMustChangePassword());
    }

    private Account requireAccount(UUID targetId) {
        return accountRepository.findById(targetId)
                .orElseThrow(() -> ApiException.notFound(
                        "user_not_found", "Пользователь не найден"));
    }

    private static UserResponse toResponse(Account account) {
        return new UserResponse(
                account.getId(),
                account.getUsername(),
                account.getDisplayName(),
                account.getEmail(),
                account.getRole().name(),
                account.isBlocked(),
                account.isMustChangePassword(),
                account.getCreatedAt(),
                account.getUpdatedAt());
    }

    private static ApiException usernameTaken() {
        return ApiException.conflict("username_taken", "Пользователь с таким логином уже существует");
    }

    /**
     * Maps a race-condition integrity violation to the same response the pre-check
     * would have produced. The single-ADMIN index is unreachable here (the role is
     * validated earlier) but is handled defensively so a future change cannot leak a
     * raw 500.
     */
    private static ApiException translateIntegrityViolation(DataIntegrityViolationException ex) {
        String cause = ex.getMostSpecificCause().getMessage();
        if (cause != null && (cause.contains(USERNAME_CONSTRAINT)
                || cause.contains(USERNAME_NORMALIZED_CONSTRAINT))) {
            return usernameTaken();
        }
        if (cause != null && cause.contains(SINGLE_ADMIN_CONSTRAINT)) {
            return new ApiException(HttpStatus.BAD_REQUEST, "admin_not_assignable",
                    "Администратор в системе один, назначить эту роль нельзя");
        }
        throw ex;
    }

    private static String normalizeUsername(String username) {
        return username.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
