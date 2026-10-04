package ru.nstu.system.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * Refresh token row backed by {@code auth.refresh_token} (design.md D6, D8).
 *
 * <p>Only the SHA-256 hash of the opaque token is stored, never the raw value.
 * Rotation marks the consumed token {@code revoked} and links the replacement via
 * {@code rotatedFrom}, which enables the reuse-detection rule: presenting an
 * already-revoked token revokes every active token of the account.</p>
 */
@Entity
@Table(name = "refresh_token", schema = "auth")
public class RefreshToken {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "token_hash", nullable = false, length = 128, updatable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked", nullable = false)
    private boolean revoked;

    @Column(name = "rotated_from", updatable = false)
    private UUID rotatedFrom;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Required by JPA. */
    protected RefreshToken() {
    }

    private RefreshToken(UUID id,
                         UUID accountId,
                         String tokenHash,
                         Instant expiresAt,
                         boolean revoked,
                         UUID rotatedFrom,
                         Instant createdAt) {
        this.id = id;
        this.accountId = accountId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.revoked = revoked;
        this.rotatedFrom = rotatedFrom;
        this.createdAt = createdAt;
    }

    /**
     * Issues a new active refresh token.
     *
     * @param accountId  owning account
     * @param tokenHash  SHA-256 hash of the opaque token (never the raw value)
     * @param expiresAt  absolute expiry instant
     * @param rotatedFrom id of the token this one replaces, or {@code null} on a
     *                    fresh login / password change
     */
    public static RefreshToken issue(UUID accountId, String tokenHash, Instant expiresAt, UUID rotatedFrom) {
        return new RefreshToken(
                UUID.randomUUID(),
                Objects.requireNonNull(accountId, "accountId"),
                Objects.requireNonNull(tokenHash, "tokenHash"),
                Objects.requireNonNull(expiresAt, "expiresAt").truncatedTo(ChronoUnit.MICROS),
                false,
                rotatedFrom,
                Instant.now().truncatedTo(ChronoUnit.MICROS));
    }

    /** Marks this token as no longer usable. */
    public void revoke() {
        this.revoked = true;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public UUID getRotatedFrom() {
        return rotatedFrom;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
