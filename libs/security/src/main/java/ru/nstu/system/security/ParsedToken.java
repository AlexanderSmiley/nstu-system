package ru.nstu.system.security;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Validated content of an access token.
 *
 * <p>Produced exclusively by {@link AccessTokenParser}: by the time an instance
 * exists the signature, the issuer and the expiration have already been checked,
 * so consumers can trust every field.</p>
 *
 * @param subject                  account identifier, or {@code guest:<uuid>} for
 *                                 anonymous guest sessions (design.md D11)
 * @param roles                    role names without the {@code ROLE_} prefix
 * @param passwordChangeRequired   {@code true} while a mandatory password change is
 *                                 pending; such a token may only reach the routes
 *                                 allowed by {@link PasswordChangePolicy}
 * @param expiresAt                expiration instant ({@code exp})
 * @param issuedAt                 issuance instant ({@code iat}); may be {@code null}
 *                                 for tokens issued by an external producer
 */
public record ParsedToken(
        String subject,
        Set<String> roles,
        boolean passwordChangeRequired,
        Instant expiresAt,
        Instant issuedAt) {

    public ParsedToken {
        Objects.requireNonNull(subject, "subject must not be null");
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }
}
