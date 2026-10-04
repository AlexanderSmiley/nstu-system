package ru.nstu.system.auth.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/users} (identity spec "Создание пользователя
 * администратором", task 5.8).
 *
 * <p>The role is carried as text on purpose: an unknown or forbidden role must
 * yield {@code 400} with a clear Russian message rather than a deserialisation
 * failure. The assignable set ({@code STAFF}, {@code STUDENT}) is enforced in the
 * service layer together with the single-administrator rule.</p>
 *
 * @param username    unique login name, matched case-insensitively
 * @param displayName human-readable name; for students/staff the profile name
 * @param role        requested role name, only {@code STAFF} or {@code STUDENT}
 * @param email       optional email (stored only in {@code auth.account})
 */
public record CreateUserRequest(
        @NotBlank(message = "Укажите имя пользователя")
        @Size(max = 64, message = "Имя пользователя не должно превышать 64 символа")
        String username,

        @NotBlank(message = "Укажите отображаемое имя")
        @Size(max = 255, message = "Отображаемое имя не должно превышать 255 символов")
        String displayName,

        @NotBlank(message = "Укажите роль")
        String role,

        @Email(message = "Некорректный адрес электронной почты")
        @Size(max = 255, message = "Адрес электронной почты не должен превышать 255 символов")
        String email) {
}
