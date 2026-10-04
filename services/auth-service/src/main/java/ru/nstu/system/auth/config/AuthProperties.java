package ru.nstu.system.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Authentication cookie and session lifetimes (design.md D6, D8, D11).
 *
 * <p>Bound from {@code nstu.auth.*}. Defaults allow a local run over plain HTTP:
 * cookies are not marked {@code Secure} unless {@code cookie-secure} is enabled
 * (production sets {@code COOKIE_SECURE=true}).</p>
 */
@Component
@ConfigurationProperties(prefix = "nstu.auth")
public class AuthProperties {

    /** Refresh-token lifetime; also the refresh cookie {@code Max-Age}. */
    public static final Duration DEFAULT_REFRESH_TTL = Duration.ofDays(7);

    /** Guest access-token lifetime (no refresh token exists, design.md D11). */
    public static final Duration DEFAULT_GUEST_TTL = Duration.ofDays(7);

    private boolean cookieSecure;

    private Duration refreshTtl = DEFAULT_REFRESH_TTL;

    private Duration guestTtl = DEFAULT_GUEST_TTL;

    public boolean isCookieSecure() {
        return cookieSecure;
    }

    public void setCookieSecure(boolean cookieSecure) {
        this.cookieSecure = cookieSecure;
    }

    public Duration getRefreshTtl() {
        return refreshTtl;
    }

    public void setRefreshTtl(Duration refreshTtl) {
        this.refreshTtl = refreshTtl;
    }

    public Duration getGuestTtl() {
        return guestTtl;
    }

    public void setGuestTtl(Duration guestTtl) {
        this.guestTtl = guestTtl;
    }
}
