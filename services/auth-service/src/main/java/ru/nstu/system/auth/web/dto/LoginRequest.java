package ru.nstu.system.auth.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Body of {@code POST /api/auth/login}.
 *
 * @param username username as entered; looked up case-insensitively
 * @param password raw password, verified against the BCrypt hash
 */
public record LoginRequest(
        @NotBlank(message = "Укажите имя пользователя") String username,
        @NotBlank(message = "Укажите пароль") String password) {
}
