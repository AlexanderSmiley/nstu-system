package ru.nstu.system.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * Creates and hashes opaque refresh tokens (design.md D6, D8).
 *
 * <p>A token is 32 bytes (256 bits) of {@link SecureRandom} output encoded with
 * Base64URL without padding (43 characters). Only its SHA-256 hash — a 64-character
 * lower-case hex string — is persisted in {@code auth.refresh_token.token_hash};
 * the raw value never leaves the process.</p>
 */
@Component
public class RefreshTokenGenerator {

    /** 256 bits of entropy, matching the HS256 access-token strength. */
    static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom;

    public RefreshTokenGenerator() {
        this(new SecureRandom());
    }

    RefreshTokenGenerator(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    /**
     * @return a fresh, URL-safe opaque refresh token
     */
    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Hashes a raw refresh token for storage and lookup.
     *
     * @param rawToken raw token value
     * @return lower-case hex SHA-256 digest
     */
    public String hash(String rawToken) {
        if (rawToken == null) {
            throw new IllegalArgumentException("rawToken must not be null");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available in this JVM", ex);
        }
    }
}
