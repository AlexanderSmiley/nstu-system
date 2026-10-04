package ru.nstu.system.auth.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PATCH /api/users/{id}} (identity spec "Редактирование данных
 * пользователя", task 5.8).
 *
 * <p>Every field is optional: an omitted (or {@code null}) field keeps its current
 * value. A request with all three fields absent is rejected by the service. The
 * role is text for the same reason as in {@link CreateUserRequest}; only
 * {@code STAFF}/{@code STUDENT} are assignable and {@code ADMIN}/{@code GUEST} are
 * rejected with {@code 400}.</p>
 *
 * @param displayName new display name, or {@code null} to keep the current one
 * @param email       new email, or {@code null} to keep the current one
 * @param role        requested role, or {@code null} to keep the current one
 */
public record UpdateUserRequest(
        @Size(max = 255, message = "Отображаемое имя не должно превышать 255 символов")
        String displayName,

        @Email(message = "Некорректный адрес электронной почты")
        @Size(max = 255, message = "Адрес электронной почты не должен превышать 255 символов")
        String email,

        String role) {
}
