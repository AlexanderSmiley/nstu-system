package ru.nstu.system.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Account aggregate root backed by {@code auth.account}.
 *
 * <p>Only the fields required by the administrator bootstrap are mapped here;
 * later tasks extend this entity as the account API is implemented. The mapping
 * is validated at startup ({@code spring.jpa.hibernate.ddl-auto=validate})
 * against the Flyway migration.</p>
 */
@Entity
@Table(name = "account", schema = "auth")
public class Account {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "username", nullable = false, length = 64)
    private String username;

    @Column(name = "username_normalized", nullable = false, length = 64)
    private String usernameNormalized;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Column(name = "email", length = 255)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private Role role;

    @Column(name = "blocked", nullable = false)
    private boolean blocked;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Required by JPA. */
    protected Account() {
    }

    private Account(UUID id,
                    String username,
                    String usernameNormalized,
                    String passwordHash,
                    String displayName,
                    String email,
                    Role role,
                    boolean blocked,
                    boolean mustChangePassword,
                    Instant createdAt,
                    Instant updatedAt) {
        this.id = id;
        this.username = username;
        this.usernameNormalized = usernameNormalized;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.email = email;
        this.role = role;
        this.blocked = blocked;
        this.mustChangePassword = mustChangePassword;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Creates the single bootstrap administrator.
     *
     * <p>The account is always created with {@code mustChangePassword = true}
     * (design.md D9, identity spec "Бутстрап администратора при установке").</p>
     *
     * @param username           username as entered in {@code ADMIN_USERNAME}
     * @param usernameNormalized lower-cased, trimmed username for lookups
     * @param passwordHash       BCrypt hash of {@code ADMIN_PASSWORD}
     * @return a new, not-yet-persisted administrator account
     */
    public static Account createAdmin(String username, String usernameNormalized, String passwordHash) {
        return create(username, usernameNormalized, passwordHash, username, null, Role.ADMIN, false, true);
    }

    /**
     * Creates a new account.
     *
     * <p>Used for administrator-created users (design.md D2, identity spec
     * "Создание пользователя администратором") and by tests. New accounts start
     * with {@code mustChangePassword = true} unless explicitly disabled.</p>
     *
     * @param username           username as entered
     * @param usernameNormalized lower-cased, trimmed username for lookups
     * @param passwordHash       BCrypt hash of the password
     * @param displayName        display name (for the administrator it equals the username)
     * @param email              optional email; personal data live in the student profile
     * @param role               persisted role; {@code GUEST} is not persistable
     * @param blocked            whether the account is blocked
     * @param mustChangePassword whether a mandatory password change is pending
     * @return a new, not-yet-persisted account
     */
    public static Account create(String username,
                                 String usernameNormalized,
                                 String passwordHash,
                                 String displayName,
                                 String email,
                                 Role role,
                                 boolean blocked,
                                 boolean mustChangePassword) {
        Instant now = Instant.now();
        return new Account(
                UUID.randomUUID(),
                Objects.requireNonNull(username, "username"),
                Objects.requireNonNull(usernameNormalized, "usernameNormalized"),
                Objects.requireNonNull(passwordHash, "passwordHash"),
                displayName,
                email,
                Objects.requireNonNull(role, "role"),
                blocked,
                mustChangePassword,
                now,
                now);
    }

    /**
     * Replaces the password hash and clears the mandatory-change flag
     * (identity spec "Успешная смена пароля", design.md D9).
     *
     * <p>The caller owns the transaction and is responsible for revoking the
     * account's refresh tokens (design.md D8).</p>
     *
     * @param newPasswordHash BCrypt hash of the new password
     */
    public void changePassword(String newPasswordHash) {
        this.passwordHash = Objects.requireNonNull(newPasswordHash, "newPasswordHash");
        this.mustChangePassword = false;
        this.updatedAt = Instant.now();
    }

    /**
     * Replaces the administrator-editable attributes (identity spec "Редактирование
     * данных пользователя", task 5.8).
     *
     * <p>Only the display name, the optional email and the role can change here;
     * the username, the password hash and the {@code blocked} flag are not part of
     * this operation (blocking is task 5.10). The caller owns the transaction and
     * the role/self-demotion policy checks.</p>
     *
     * @param displayName new display name
     * @param email       new optional email (may be {@code null})
     * @param role        new persisted role (never {@code ADMIN} through this path)
     */
    public void updateDetails(String displayName, String email, Role role) {
        this.displayName = displayName;
        this.email = email;
        this.role = Objects.requireNonNull(role, "role");
        this.updatedAt = Instant.now();
    }

    /**
     * Blocks the account (identity spec "Блокировка и разблокировка пользователя",
     * task 5.10). The caller owns the transaction, the administrator-safety policy
     * and the revocation of the account's refresh tokens (design.md D8).
     */
    public void block() {
        this.blocked = true;
        this.updatedAt = Instant.now();
    }

    /**
     * Lifts a block (identity spec "Блокировка и разблокировка пользователя",
     * task 5.10). The caller owns the transaction.
     */
    public void unblock() {
        this.blocked = false;
        this.updatedAt = Instant.now();
    }

    /**
     * Installs an administrator-issued temporary password and re-arms the mandatory
     * password change (identity spec "Сброс пароля администратором", task 5.10).
     *
     * <p>Unlike {@link #changePassword(String)}, which is the user's own successful
     * change, this deliberately sets {@code mustChangePassword = true} so the user
     * must pick a new password on the next login. The caller owns the transaction
     * and is responsible for revoking the account's refresh tokens (design.md D8).</p>
     *
     * @param newPasswordHash BCrypt hash of the generated temporary password
     */
    public void resetPassword(String newPasswordHash) {
        this.passwordHash = Objects.requireNonNull(newPasswordHash, "newPasswordHash");
        this.mustChangePassword = true;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getUsernameNormalized() {
        return usernameNormalized;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmail() {
        return email;
    }

    public Role getRole() {
        return role;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
