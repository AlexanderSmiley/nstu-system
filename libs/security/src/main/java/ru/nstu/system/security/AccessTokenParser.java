package ru.nstu.system.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * Verifies and parses HS256 access tokens (design.md D6, D10).
 *
 * <p>The parser rejects anything that is not a correctly signed, unexpired token
 * issued by the configured issuer; every failure surfaces as
 * {@link InvalidTokenException} so callers can map it to HTTP 401.</p>
 */
@Component
public class AccessTokenParser {

    private final JwtProperties properties;

    private final SecretKey key;

    public AccessTokenParser(JwtProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.key = JwtKeys.from(properties);
    }

    /**
     * Validates and decodes an access token.
     *
     * @param token compact JWS string
     * @return the trusted token content
     * @throws InvalidTokenException if the token is missing, malformed, forged,
     *                               expired or issued by another issuer
     */
    public ParsedToken parse(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidTokenException("access token is missing");
        }
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(properties.getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new InvalidTokenException("access token has expired", e);
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException("access token is invalid", e);
        }

        Instant expiresAt = claims.getExpiration() == null
                ? null
                : claims.getExpiration().toInstant();
        if (expiresAt == null) {
            throw new InvalidTokenException("access token has no expiration");
        }
        Instant issuedAt = claims.getIssuedAt() == null
                ? null
                : claims.getIssuedAt().toInstant();
        return new ParsedToken(
                claims.getSubject(),
                extractRoles(claims),
                extractPasswordChangeRequired(claims),
                expiresAt,
                issuedAt);
    }

    private Set<String> extractRoles(Claims claims) {
        Object raw = claims.get(TokenIssuer.CLAIM_ROLES);
        if (raw == null) {
            return Set.of();
        }
        Set<String> roles = new LinkedHashSet<>();
        if (raw instanceof Collection<?> collection) {
            for (Object element : collection) {
                if (element != null) {
                    roles.add(element.toString());
                }
            }
        } else {
            roles.add(raw.toString());
        }
        return Set.copyOf(roles);
    }

    private boolean extractPasswordChangeRequired(Claims claims) {
        Object raw = claims.get(TokenIssuer.CLAIM_PASSWORD_CHANGE_REQUIRED);
        if (raw instanceof Boolean value) {
            return value;
        }
        return raw != null && Boolean.parseBoolean(raw.toString());
    }
}
