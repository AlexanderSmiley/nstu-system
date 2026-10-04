package ru.nstu.system.auth.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link RefreshToken} (design.md D6, D8).
 *
 * <p>{@link #findByTokenHash(String)} is unique-backed (the {@code
 * refresh_token_token_hash_idx} index), so the login and refresh flows never need
 * to resolve duplicates.</p>
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findAllByAccountIdAndRevokedFalse(UUID accountId);

    long countByAccountIdAndRevokedFalse(UUID accountId);
}
