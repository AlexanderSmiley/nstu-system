package ru.nstu.system.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Shared configuration of the single symmetric JWT secret (design.md D6).
 *
 * <p>Bound from the {@code nstu.jwt} prefix. The secret is supplied through the
 * environment ({@code NSTU_JWT_SECRET}) and must never be committed to the
 * repository. HS256 requires at least 256 bits of key material, therefore the
 * secret must be at least 32 ASCII characters long; beans built from these
 * properties fail fast with a clear message otherwise.</p>
 *
 * <p>Annotated with {@link Component} so services that scan the
 * {@code ru.nstu.system} package receive the bean without extra wiring.</p>
 */
@Component
@ConfigurationProperties(prefix = "nstu.jwt")
public class JwtProperties {

    /** Minimum number of bytes required for an HS256 key. */
    public static final int MIN_SECRET_LENGTH = 32;

    private String issuer = "nstu-system";

    private String secret;

    private Duration accessTtl = Duration.ofMinutes(15);

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public Duration getAccessTtl() {
        return accessTtl;
    }

    public void setAccessTtl(Duration accessTtl) {
        this.accessTtl = accessTtl;
    }
}
