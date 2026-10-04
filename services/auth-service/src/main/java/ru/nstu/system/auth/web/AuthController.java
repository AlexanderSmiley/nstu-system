package ru.nstu.system.auth.web;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.nstu.system.auth.config.AuthCookies;
import ru.nstu.system.auth.service.AuthService;
import ru.nstu.system.auth.service.IssuedGuestSession;
import ru.nstu.system.auth.service.IssuedSession;
import ru.nstu.system.auth.web.dto.LoginRequest;
import ru.nstu.system.auth.web.dto.MeResponse;
import ru.nstu.system.auth.web.dto.PasswordChangeRequest;
import ru.nstu.system.auth.web.dto.ProfileResponse;
import ru.nstu.system.security.ParsedToken;
import ru.nstu.system.security.SecurityContextSupport;

/**
 * Authentication HTTP API, mounted under {@code /api/auth} (design.md D4, D6-D9).
 *
 * <p>Tokens are never returned in the body; they are written as httpOnly cookies by
 * {@link AuthCookies}. Public endpoints (login, refresh, guest) are declared in
 * {@link ru.nstu.system.auth.config.AuthSecurityConfig}; the remaining endpoints
 * require a valid access token.</p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthCookies cookies;

    public AuthController(AuthService authService, AuthCookies cookies) {
        this.authService = authService;
        this.cookies = cookies;
    }

    /** Public: authenticates a user and opens a session (task 5.1). */
    @PostMapping("/login")
    public ResponseEntity<ProfileResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {
        IssuedSession session = authService.login(request.username(), request.password());
        writeSessionCookies(response, session);
        return ResponseEntity.ok(session.profile());
    }

    /** Public: rotates the refresh token and issues a new session (task 5.2). */
    @PostMapping("/refresh")
    public ResponseEntity<ProfileResponse> refresh(
            @CookieValue(name = AuthCookies.REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        IssuedSession session = authService.refresh(refreshToken);
        writeSessionCookies(response, session);
        return ResponseEntity.ok(session.profile());
    }

    /** Public: creates an anonymous guest session (task 5.6). */
    @PostMapping("/guest")
    public ResponseEntity<MeResponse> guest(HttpServletResponse response) {
        IssuedGuestSession session = authService.guest();
        cookies.writeAccess(response, session.accessToken(), session.accessTtl());
        return ResponseEntity.ok(session.profile());
    }

    /** Revokes the current refresh token and expires the session cookies (task 5.3). */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = AuthCookies.REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        authService.logout(refreshToken);
        cookies.clear(response);
        return ResponseEntity.ok().build();
    }

    /** Returns the identity behind the current access token (task 5.4). */
    @GetMapping("/me")
    public MeResponse me() {
        return authService.me(currentToken());
    }

    /** Changes the password and starts a fresh full session (task 5.5). */
    @PostMapping("/password")
    public ResponseEntity<ProfileResponse> changePassword(
            @Valid @RequestBody PasswordChangeRequest request,
            HttpServletResponse response) {
        IssuedSession session = authService.changePassword(
                currentToken(), request.oldPassword(), request.newPassword());
        writeSessionCookies(response, session);
        return ResponseEntity.ok(session.profile());
    }

    private void writeSessionCookies(HttpServletResponse response, IssuedSession session) {
        cookies.writeAccess(response, session.accessToken(), session.accessTtl());
        cookies.writeRefresh(response, session.refreshToken());
    }

    private static ParsedToken currentToken() {
        return SecurityContextSupport.currentToken()
                .orElseThrow(() -> ApiException.unauthorized("unauthorized", "Требуется аутентификация"));
    }
}
