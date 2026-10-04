package ru.nstu.system.auth.service;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.nstu.system.auth.domain.RefreshToken;
import ru.nstu.system.auth.domain.RefreshTokenRepository;

/**
 * Revokes every active refresh token of an account in its own transaction
 * (design.md D8).
 *
 * <p>Used on failure paths that must revoke tokens <em>and</em> then fail the
 * request (reuse of a rotated token, blocked account on refresh). The surrounding
 * service method is transactional and rolls back when it throws, so a plain
 * in-transaction update would be discarded; {@code REQUIRES_NEW} commits the
 * revocation before the exception propagates.</p>
 */
@Component
public class RefreshTokenRevoker {

    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshTokenRevoker(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    /**
     * Revokes all non-revoked refresh tokens of the account in a new transaction.
     *
     * @param accountId owning account
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeAllActive(UUID accountId) {
        List<RefreshToken> active = refreshTokenRepository.findAllByAccountIdAndRevokedFalse(accountId);
        if (active.isEmpty()) {
            return;
        }
        active.forEach(RefreshToken::revoke);
        refreshTokenRepository.saveAll(active);
    }
}
