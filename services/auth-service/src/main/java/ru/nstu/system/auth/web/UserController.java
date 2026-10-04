package ru.nstu.system.auth.web;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.nstu.system.auth.service.UserManagementService;
import ru.nstu.system.auth.web.dto.CreateUserRequest;
import ru.nstu.system.auth.web.dto.CreatedUserResponse;
import ru.nstu.system.auth.web.dto.PasswordResetResponse;
import ru.nstu.system.auth.web.dto.UpdateUserRequest;
import ru.nstu.system.auth.web.dto.UserResponse;
import ru.nstu.system.security.ParsedToken;
import ru.nstu.system.security.SecurityContextSupport;

/**
 * Administrator user-management API, mounted under {@code /api/users}
 * (identity spec "Создание пользователя администратором", "Редактирование данных
 * пользователя", "Просмотр списка пользователей"; tasks 5.8/5.11).
 *
 * <p>Access is restricted to {@code ADMIN} in
 * {@link ru.nstu.system.auth.config.AuthSecurityConfig}; this controller is a thin
 * adapter and owns no business rules. The temporary password is returned only by
 * {@code POST /api/users} and never by read or update endpoints.</p>
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserManagementService userManagementService;

    public UserController(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    /** Creates a {@code STAFF}/{@code STUDENT} account (task 5.8). */
    @PostMapping
    public ResponseEntity<CreatedUserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userManagementService.create(request));
    }

    /** Lists all accounts (task 5.8). */
    @GetMapping
    public List<UserResponse> list() {
        return userManagementService.list();
    }

    /** Updates display name, email or role of an account (task 5.8). */
    @PatchMapping("/{id}")
    public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return userManagementService.update(currentAccountId(), id, request);
    }

    /** Blocks an account and revokes its sessions (task 5.10). */
    @PostMapping("/{id}/block")
    public UserResponse block(@PathVariable UUID id) {
        return userManagementService.block(currentAccountId(), id);
    }

    /** Lifts a block (task 5.10). */
    @PostMapping("/{id}/unblock")
    public UserResponse unblock(@PathVariable UUID id) {
        return userManagementService.unblock(id);
    }

    /** Resets an account's password and returns the new one-time password (task 5.10). */
    @PostMapping("/{id}/reset-password")
    public PasswordResetResponse resetPassword(@PathVariable UUID id) {
        return userManagementService.resetPassword(id);
    }

    private static UUID currentAccountId() {
        ParsedToken token = SecurityContextSupport.currentToken()
                .orElseThrow(() -> ApiException.unauthorized("unauthorized", "Требуется аутентификация"));
        try {
            return UUID.fromString(token.subject());
        } catch (IllegalArgumentException ex) {
            throw ApiException.unauthorized("unauthorized", "Сессия недействительна");
        }
    }
}
