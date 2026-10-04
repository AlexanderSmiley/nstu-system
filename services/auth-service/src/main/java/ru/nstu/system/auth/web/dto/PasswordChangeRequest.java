package ru.nstu.system.auth.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Body of {@code POST /api/auth/password} (design.md D9).
 *
 * <p>Both fields are optional at the bean-validation level; the semantic policy
 * (old password matches, new password ≥ 8 chars, letters and digits, differs from
 * the current one) is enforced in the service with clear messages.</p>
 *
 * @param oldPassword current (possibly temporary) password
 * @param newPassword desired new password
 */
public record PasswordChangeRequest(
        @NotBlank(message = "Укажите текущий пароль") String oldPassword,
        @NotBlank(message = "Укажите новый пароль") String newPassword) {
}
