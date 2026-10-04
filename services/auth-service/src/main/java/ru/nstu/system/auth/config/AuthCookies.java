package ru.nstu.system.auth.config;

import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Builds the session cookies (design.md D6, D11).
 *
 * <p>Contract shared with the gateway and the SPA:</p>
 * <ul>
 *   <li>names {@code access_token} and {@code refresh_token};</li>
 *   <li>{@code HttpOnly}, {@code SameSite=Lax}, {@code Path=/};</li>
 *   <li>{@code Secure} only when {@code nstu.auth.cookie-secure} is enabled;</li>
 *   <li>{@code Max-Age} equal to the token lifetime (access 15 min, refresh 7 days,
 *       guest 7 days).</li>
 * </ul>
 *
 * <p>Cookies are written as raw {@code Set-Cookie} headers (not via
 * {@link jakarta.servlet.http.Cookie}) because the Servlet cookie API cannot
 * express {@code SameSite} portably.</p>
 */
@Component
public class AuthCookies {

    /** Cookie carrying the short-lived access token. */
    public static final String ACCESS_COOKIE = "access_token";

    /** Cookie carrying the opaque, rotatable refresh token. */
    public static final String REFRESH_COOKIE = "refresh_token";

    private static final String SAME_SITE = "Lax";

    private static final String ROOT_PATH = "/";

    private final AuthProperties properties;

    public AuthCookies(AuthProperties properties) {
        this.properties = properties;
    }

    /** Writes the access cookie with the given lifetime. */
    public void writeAccess(HttpServletResponse response, String token, Duration maxAge) {
        addCookie(response, ACCESS_COOKIE, token, maxAge);
    }

    /** Writes the refresh cookie with the configured refresh lifetime. */
    public void writeRefresh(HttpServletResponse response, String token) {
        addCookie(response, REFRESH_COOKIE, token, properties.getRefreshTtl());
    }

    /**
     * Expires both session cookies ({@code Max-Age=0}) while preserving the same
     * attributes, so the browser removes exactly the cookies that were set.
     */
    public void clear(HttpServletResponse response) {
        addCookie(response, ACCESS_COOKIE, "", Duration.ZERO);
        addCookie(response, REFRESH_COOKIE, "", Duration.ZERO);
    }

    private void addCookie(HttpServletResponse response, String name, String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(properties.isCookieSecure())
                .path(ROOT_PATH)
                .sameSite(SAME_SITE)
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
