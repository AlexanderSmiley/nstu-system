package ru.nstu.system.security;

import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;

/**
 * Derives the HS256 signing key from {@link JwtProperties}.
 *
 * <p>Kept package-private: the key never leaves the security module. The secret
 * is interpreted as raw UTF-8 text, which keeps the byte length (and therefore
 * the {@link JwtProperties#MIN_SECRET_LENGTH} check) predictable.</p>
 */
final class JwtKeys {

    private JwtKeys() {
    }

    /**
     * Builds an HMAC-SHA key from the configured secret.
     *
     * @throws IllegalStateException if the secret is missing or too short for HS256
     */
    static SecretKey from(JwtProperties properties) {
        String secret = properties.getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "nstu.jwt.secret is not configured; provide NSTU_JWT_SECRET via the environment");
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < JwtProperties.MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "nstu.jwt.secret must be at least " + JwtProperties.MIN_SECRET_LENGTH
                            + " bytes for HS256 (got " + keyBytes.length
                            + "); use a longer random value");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
