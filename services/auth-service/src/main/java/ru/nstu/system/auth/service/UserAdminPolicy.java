package ru.nstu.system.auth.service;

import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.nstu.system.auth.domain.Account;
import ru.nstu.system.auth.domain.Role;
import ru.nstu.system.auth.web.ApiException;

/**
 * Business rules guarding role assignment and administrator safety
 * (identity spec "Единственный администратор" and "Фиксированный набор ролей",
 * task 5.9).
 *
 * <p>Kept separate from {@link UserManagementService} so the rules are explicit,
 * reusable by the blocking/password-reset endpoints (task 5.10) and unit-testable
 * without a Spring context. Every violation is an {@link ApiException} with a
 * stable machine-readable code and a Russian message.</p>
 *
 * <p>The system supports exactly one {@code ADMIN}. Creation and promotion to
 * {@code ADMIN} are therefore refused, as is demoting or blocking the last (and
 * currently only) administrator. Self-demotion and self-blocking are refused with
 * distinct messages.</p>
 */
@Component
public class UserAdminPolicy {

    /**
     * Parses a requested role name and rejects everything that may not be assigned
     * to an account created by an administrator.
     *
     * @param rawRole role name as sent by the client
     * @return {@link Role#STAFF} or {@link Role#STUDENT}
     * @throws ApiException 400 for a blank/unknown role, {@code ADMIN} or {@code GUEST}
     */
    public Role requireAssignableRole(String rawRole) {
        if (rawRole == null || rawRole.isBlank()) {
            throw ApiException.badRequest("invalid_role", "Укажите роль");
        }
        String normalized = rawRole.trim().toUpperCase(Locale.ROOT);
        if ("GUEST".equals(normalized)) {
            throw ApiException.badRequest("guest_not_assignable",
                    "Роль GUEST нельзя назначить учётной записи");
        }
        if ("ADMIN".equals(normalized)) {
            throw ApiException.badRequest("admin_not_assignable",
                    "Администратор в системе один, назначить эту роль нельзя");
        }
        try {
            return Role.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("invalid_role", "Неизвестная роль: " + rawRole.trim());
        }
    }

    /**
     * Enforces the role-change rules: only {@code STAFF}/{@code STUDENT} are
     * assignable, an administrator may not change their own role, and the last
     * administrator may not be demoted.
     *
     * @param actorId  account id performing the change
     * @param target   account being changed
     * @param newRole  already parsed assignable role
     * @throws ApiException 400 when the change is forbidden
     */
    public void ensureRoleChangeAllowed(UUID actorId, Account target, Role newRole) {
        if (newRole == target.getRole()) {
            return;
        }
        if (target.getId().equals(actorId)) {
            throw ApiException.badRequest("self_demotion_forbidden",
                    "Нельзя изменить собственную роль");
        }
        if (target.getRole() == Role.ADMIN) {
            throw ApiException.badRequest("last_admin_required",
                    "Нельзя понизить единственного администратора");
        }
    }

    /**
     * Enforces the blocking rules: an administrator may not block themselves and the
     * last administrator may not be blocked (otherwise the system would be left
     * without one).
     *
     * <p>Used by the block endpoint added in task 5.10; covered directly by unit
     * tests here because this task only implements role editing.</p>
     *
     * @param actorId account id performing the block
     * @param target  account being blocked
     * @throws ApiException 400 when the block is forbidden
     */
    public void ensureBlockAllowed(UUID actorId, Account target) {
        if (target.getId().equals(actorId)) {
            throw ApiException.badRequest("self_block_forbidden",
                    "Нельзя заблокировать собственную учётную запись");
        }
        if (target.getRole() == Role.ADMIN) {
            throw ApiException.badRequest("last_admin_required",
                    "Нельзя заблокировать единственного администратора");
        }
    }
}
