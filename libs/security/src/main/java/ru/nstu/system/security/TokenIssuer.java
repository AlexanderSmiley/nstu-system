package ru.nstu.system.security;

import io.jsonwebtoken.Jwts;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Issues HS256 access tokens (design.md D6, D7).
 *
 * <p>Claims produced: {@code iss}, {@code sub}, {@code iat}, {@code exp},
 * {@code roles} and {@code pwd_change_required}. Only {@code auth-service} is
 * expected to use this class; every other component only needs
 * {@link AccessTokenParser}.</p>
 */
@Component
public class TokenIssuer {

    /** Claim holding the set of role names. */
    static final String CLAIM_ROLES = "roles";

    /** Claim marking a token that may only change the password or log out. */
    static final String CLAIM_PASSWORD_CHANGE_REQUIRED = "pwd_change_required";

    private final JwtProperties properties;

    private final Clock clock;

    private final SecretKey key;

    @Autowired
    public TokenIssuer(JwtProperties properties) {
        this(properties, Clock.systemUTC());
    }

    public TokenIssuer(JwtProperties properties, Clock clock) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.key = JwtKeys.from(properties);
    }

    /**
     * Issues a signed access token.
     *
     * @param subject                account id or {@code guest:<uuid>}
     * @param roles                  role names without the {@code ROLE_} prefix
     * @param passwordChangeRequired {@code true} to issue a restricted token
     * @return the compact JWS string
     */
    public String issueAccessToken(String subject, Set<String> roles, boolean passwordChangeRequired) {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("subject must not be blank");
        }
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.getAccessTtl());
        List<String> roleList = roles == null ? List.of() : List.copyOf(roles);
        return Jwts.builder()
                .issuer(properties.getIssuer())
                .subject(subject)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .claim(CLAIM_ROLES, roleList)
                .claim(CLAIM_PASSWORD_CHANGE_REQUIRED, passwordChangeRequired)
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /** Exposes the configured access-token lifetime. */
    public java.time.Duration accessTtl() {
        return properties.getAccessTtl();
    }
}
