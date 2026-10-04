package ru.nstu.system.security.servlet;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.http.HttpHeaders;

/**
 * Extracts the raw access token from a servlet request (design.md D6, D10).
 *
 * <p>The browser client stores the access token in the httpOnly
 * {@code access_token} cookie, while non-browser clients (tests, service-to-service
 * calls, future mobile apps) pass {@code Authorization: Bearer <token>}. Cookie
 * lookup has priority so that a stale {@code Authorization} header cannot shadow a
 * freshly rotated cookie; the bearer header is the fallback.</p>
 *
 * <p>This is the servlet counterpart of the token extraction performed by the
 * reactive gateway. It is shared by every servlet service through
 * {@code ru.nstu:security} so the rule cannot drift.</p>
 */
public final class BearerOrCookieTokenResolver {

    /** Name of the httpOnly cookie carrying the access token. */
    public static final String ACCESS_TOKEN_COOKIE = "access_token";

    private static final String BEARER_PREFIX = "Bearer ";

    private BearerOrCookieTokenResolver() {
    }

    /**
     * Resolves the raw access token of a request.
     *
     * @param request current servlet request; may be {@code null}
     * @return the token from the {@code access_token} cookie if present and
     *         non-blank, otherwise from a well-formed {@code Authorization: Bearer}
     *         header, otherwise {@link Optional#empty()}
     */
    public static Optional<String> resolve(HttpServletRequest request) {
        if (request == null) {
            return Optional.empty();
        }
        String cookieValue = fromCookie(request);
        if (cookieValue != null) {
            return Optional.of(cookieValue);
        }
        return fromAuthorizationHeader(request);
    }

    private static String fromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (cookie != null && ACCESS_TOKEN_COOKIE.equals(cookie.getName())) {
                String value = cookie.getValue();
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
        }
        return null;
    }

    private static Optional<String> fromAuthorizationHeader(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null
                || !authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return Optional.empty();
        }
        String value = authorization.substring(BEARER_PREFIX.length()).trim();
        return value.isEmpty() ? Optional.empty() : Optional.of(value);
    }
}
