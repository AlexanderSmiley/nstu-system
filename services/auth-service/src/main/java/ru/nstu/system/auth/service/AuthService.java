package ru.nstu.system.auth.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.nstu.system.auth.config.AuthProperties;
import ru.nstu.system.auth.domain.Account;
import ru.nstu.system.auth.domain.AccountRepository;
import ru.nstu.system.auth.domain.RefreshToken;
import ru.nstu.system.auth.domain.RefreshTokenRepository;
import ru.nstu.system.auth.web.ApiException;
import ru.nstu.system.auth.web.dto.MeResponse;
import ru.nstu.system.auth.web.dto.ProfileResponse;
import ru.nstu.system.security.ParsedToken;
import ru.nstu.system.security.RoleNames;
import ru.nstu.system.security.TokenIssuer;

/**
 * Core authentication use cases (identity spec, design.md D6-D9, D11).
 *
 * <p>Owns the session lifecycle: password login, refresh-token rotation with
 * reuse detection, logout, profile read, mandatory password change and anonymous
 * guest sessions. All credential material stays in this service; passwords and raw
 * refresh tokens are never logged.</p>
 */
@Service
public class AuthService {

    /** Identical message and code for "unknown user" and "wrong password". */
    private static final String INVALID_CREDENTIALS_CODE = "invalid_credentials";
    private static final String INVALID_CREDENTIALS_MESSAGE = "Неверный логин или пароль";

    private static final String INVALID_REFRESH_CODE = "invalid_refresh_token";
    private static final String INVALID_REFRESH_MESSAGE = "Сессия недействительна, войдите заново";

    private static final String GUEST_SUBJECT_PREFIX = "guest:";

    private static final int MIN_PASSWORD_LENGTH = 8;

    /** Bound on the password length accepted before BCrypt work is attempted. */
    private static final int MAX_PASSWORD_LENGTH = 200;

    private final AccountRepository accountRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final RefreshTokenRevoker refreshTokenRevoker;
    private final TokenIssuer tokenIssuer;
    private final TokenIssuer guestTokenIssuer;
    private final AuthProperties authProperties;
    private final Clock clock;

    /**
     * BCrypt hash of a throwaway value, verified when the username is unknown so
     * that the response time does not reveal whether an account exists.
     */
    private final String timingEqualisationHash;

    public AuthService(AccountRepository accountRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       RefreshTokenGenerator refreshTokenGenerator,
                       RefreshTokenRevoker refreshTokenRevoker,
                       @Qualifier("tokenIssuer") TokenIssuer tokenIssuer,
                       @Qualifier("guestTokenIssuer") TokenIssuer guestTokenIssuer,
                       AuthProperties authProperties) {
        this.accountRepository = accountRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenGenerator = refreshTokenGenerator;
        this.refreshTokenRevoker = refreshTokenRevoker;
        this.tokenIssuer = tokenIssuer;
        this.guestTokenIssuer = guestTokenIssuer;
        this.authProperties = authProperties;
        this.clock = Clock.systemUTC();
        this.timingEqualisationHash = passwordEncoder.encode("nstu-timing-equalisation");
    }

    /**
     * Authenticates a user and opens a session.
     *
     * @param username    username, matched case-insensitively
     * @param rawPassword raw password
     * @return a full session (access + refresh tokens and the profile)
     * @throws ApiException 401 for unknown username / wrong password (identical
     *                      response), 403 for a blocked account
     */
    @Transactional
    public IssuedSession login(String username, String rawPassword) {
        String normalized = normalizeUsername(username);
        Account account = accountRepository.findByUsernameNormalized(normalized).orElse(null);
        if (account == null) {
            // Burn comparable CPU time so an attacker cannot enumerate usernames.
            passwordEncoder.matches(nullToEmpty(rawPassword), timingEqualisationHash);
            throw invalidCredentials();
        }
        if (!passwordEncoder.matches(nullToEmpty(rawPassword), account.getPasswordHash())) {
            throw invalidCredentials();
        }
        if (account.isBlocked()) {
            throw ApiException.forbidden("account_blocked", "Учётная запись заблокирована");
        }

        String accessToken = tokenIssuer.issueAccessToken(
                account.getId().toString(), rolesOf(account), account.isMustChangePassword());
        String refreshToken = issueRefreshToken(account.getId());
        return new IssuedSession(profileOf(account), accessToken, refreshToken,
                tokenIssuer.accessTtl(), authProperties.getRefreshTtl());
    }

    /**
     * Rotates a refresh token and issues a new token pair.
     *
     * <p>Reuse detection: presenting an already-revoked token returns 401 and
     * revokes every active token of the account (design.md D6, D8).</p>
     *
     * @param rawRefresh raw value from the {@code refresh_token} cookie
     * @return a new session
     * @throws ApiException 401 when the token is unknown, revoked, expired or the
     *                      account is blocked/removed
     */
    @Transactional
    public IssuedSession refresh(String rawRefresh) {
        if (rawRefresh == null || rawRefresh.isBlank()) {
            throw invalidRefresh();
        }
        RefreshToken stored = refreshTokenRepository.findByTokenHash(refreshTokenGenerator.hash(rawRefresh))
                .orElseThrow(AuthService::invalidRefresh);

        if (stored.isRevoked()) {
            // The token was already rotated away: likely theft. Revoke the whole family
            // in a separate transaction so the revocation survives the 401 below.
            refreshTokenRevoker.revokeAllActive(stored.getAccountId());
            throw invalidRefresh();
        }
        if (!stored.getExpiresAt().isAfter(Instant.now(clock))) {
            throw invalidRefresh();
        }
        Account account = accountRepository.findById(stored.getAccountId()).orElse(null);
        if (account == null || account.isBlocked()) {
            refreshTokenRevoker.revokeAllActive(stored.getAccountId());
            throw invalidRefresh();
        }

        stored.revoke();
        refreshTokenRepository.save(stored);

        String newRefreshToken = refreshTokenGenerator.generate();
        RefreshToken rotated = RefreshToken.issue(
                account.getId(),
                refreshTokenGenerator.hash(newRefreshToken),
                Instant.now(clock).plus(authProperties.getRefreshTtl()),
                stored.getId());
        refreshTokenRepository.save(rotated);

        String accessToken = tokenIssuer.issueAccessToken(
                account.getId().toString(), rolesOf(account), account.isMustChangePassword());
        return new IssuedSession(profileOf(account), accessToken, newRefreshToken,
                tokenIssuer.accessTtl(), authProperties.getRefreshTtl());
    }

    /**
     * Revokes the refresh token of the current session, if one is presented.
     *
     * @param rawRefresh raw value from the {@code refresh_token} cookie; may be null
     */
    @Transactional
    public void logout(String rawRefresh) {
        if (rawRefresh == null || rawRefresh.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(refreshTokenGenerator.hash(rawRefresh))
                .ifPresent(token -> {
                    token.revoke();
                    refreshTokenRepository.save(token);
                });
    }

    /**
     * Returns the profile behind a validated access token.
     *
     * @param token parsed access token of the current request
     * @return the account profile, or the guest representation
     * @throws ApiException 401 if the account no longer exists
     */
    @Transactional(readOnly = true)
    public MeResponse me(ParsedToken token) {
        if (isGuest(token.subject())) {
            return new MeResponse(
                    token.subject(),
                    token.roles(),
                    null,
                    false,
                    true,
                    null,
                    null,
                    RoleNames.GUEST);
        }
        Account account = accountRepository.findById(accountId(token.subject()))
                .orElseThrow(() -> ApiException.unauthorized("unauthorized", "Сессия недействительна"));
        return new MeResponse(
                token.subject(),
                token.roles(),
                account.getDisplayName(),
                token.passwordChangeRequired(),
                false,
                account.getUsername(),
                account.getEmail(),
                account.getRole().name());
    }

    /**
     * Changes the password of the authenticated account and starts a fresh full
     * session (design.md D8, D9).
     *
     * <p>On success: the mandatory-change flag is cleared, all refresh tokens of
     * the account are revoked and a new access/refresh pair with
     * {@code passwordChangeRequired = false} is issued.</p>
     *
     * @param token       parsed access token of the current request
     * @param oldPassword current password, must match the stored hash
     * @param newPassword new password, must satisfy the policy and differ from the old
     * @return a full, unrestricted session
     * @throws ApiException 400 for any policy violation, 401 for a stale account,
     *                      403 for a guest session
     */
    @Transactional
    public IssuedSession changePassword(ParsedToken token, String oldPassword, String newPassword) {
        if (isGuest(token.subject())) {
            throw ApiException.forbidden("forbidden", "Гостевой сессии смена пароля недоступна");
        }
        Account account = accountRepository.findById(accountId(token.subject()))
                .orElseThrow(() -> ApiException.unauthorized("unauthorized", "Сессия недействительна"));

        if (!passwordEncoder.matches(nullToEmpty(oldPassword), account.getPasswordHash())) {
            throw ApiException.badRequest("invalid_old_password", "Неверный текущий пароль");
        }
        validateNewPassword(newPassword, oldPassword, account);

        account.changePassword(passwordEncoder.encode(newPassword));
        accountRepository.save(account);
        revokeAllActive(account.getId());

        String accessToken = tokenIssuer.issueAccessToken(
                account.getId().toString(), rolesOf(account), false);
        String refreshToken = issueRefreshToken(account.getId());
        return new IssuedSession(profileOf(account), accessToken, refreshToken,
                tokenIssuer.accessTtl(), authProperties.getRefreshTtl());
    }

    /**
     * Creates an anonymous guest session: one long-lived access token with role
     * {@code GUEST}, no refresh token and no persisted account (design.md D11).
     *
     * @return the guest session
     */
    public IssuedGuestSession guest() {
        String subject = GUEST_SUBJECT_PREFIX + UUID.randomUUID();
        Set<String> roles = Set.of(RoleNames.GUEST);
        String accessToken = guestTokenIssuer.issueAccessToken(subject, roles, false);
        return new IssuedGuestSession(
                new MeResponse(subject, roles, null, false, true, null, null, RoleNames.GUEST),
                accessToken,
                guestTokenIssuer.accessTtl());
    }

    private void validateNewPassword(String newPassword, String oldPassword, Account account) {
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw ApiException.badRequest("weak_password",
                    "Новый пароль должен содержать не менее " + MIN_PASSWORD_LENGTH + " символов");
        }
        if (newPassword.length() > MAX_PASSWORD_LENGTH) {
            throw ApiException.badRequest("weak_password", "Новый пароль слишком длинный");
        }
        boolean hasLetter = newPassword.chars().anyMatch(Character::isLetter);
        boolean hasDigit = newPassword.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw ApiException.badRequest("weak_password",
                    "Новый пароль должен содержать и буквы, и цифры");
        }
        if (newPassword.equals(oldPassword)
                || passwordEncoder.matches(newPassword, account.getPasswordHash())) {
            throw ApiException.badRequest("weak_password",
                    "Новый пароль должен отличаться от временного");
        }
    }

    private String issueRefreshToken(UUID accountId) {
        String rawToken = refreshTokenGenerator.generate();
        RefreshToken token = RefreshToken.issue(
                accountId,
                refreshTokenGenerator.hash(rawToken),
                Instant.now(clock).plus(authProperties.getRefreshTtl()),
                null);
        refreshTokenRepository.save(token);
        return rawToken;
    }

    private void revokeAllActive(UUID accountId) {
        List<RefreshToken> active = refreshTokenRepository.findAllByAccountIdAndRevokedFalse(accountId);
        if (active.isEmpty()) {
            return;
        }
        active.forEach(RefreshToken::revoke);
        refreshTokenRepository.saveAll(active);
    }

    private static ProfileResponse profileOf(Account account) {
        return new ProfileResponse(
                account.getId(),
                account.getUsername(),
                account.getDisplayName(),
                account.getRole().name(),
                account.isMustChangePassword(),
                account.getEmail());
    }

    private static Set<String> rolesOf(Account account) {
        return Set.of(account.getRole().name());
    }

    private static String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static boolean isGuest(String subject) {
        return subject != null && subject.startsWith(GUEST_SUBJECT_PREFIX);
    }

    private static UUID accountId(String subject) {
        if (subject == null) {
            throw ApiException.unauthorized("unauthorized", "Сессия недействительна");
        }
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException ex) {
            throw ApiException.unauthorized("unauthorized", "Сессия недействительна");
        }
    }

    private static ApiException invalidCredentials() {
        return ApiException.unauthorized(INVALID_CREDENTIALS_CODE, INVALID_CREDENTIALS_MESSAGE);
    }

    private static ApiException invalidRefresh() {
        return ApiException.unauthorized(INVALID_REFRESH_CODE, INVALID_REFRESH_MESSAGE);
    }
}
