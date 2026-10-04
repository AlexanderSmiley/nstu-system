package ru.nstu.system.security;

import java.util.Locale;

/**
 * Allowlist for the restricted access token (design.md D7).
 *
 * <p>While a mandatory password change is pending the only reachable routes are
 * {@code POST /api/auth/password}, {@code POST /api/auth/logout},
 * {@code GET /api/auth/me}, the public session entry points
 * {@code POST /api/auth/login}, {@code POST /api/auth/refresh},
 * {@code POST /api/auth/guest} and the public {@code GET /api/site} /
 * {@code GET /api/site/icon} (the change-password screen displays the site name
 * and its icon); everything else is denied.</p>
 *
 * <p>The public session entry points are allowlisted on purpose. The browser
 * keeps the restricted {@code access_token} cookie until the password is actually
 * changed, and the reactive gateway already exposes login/refresh/guest as
 * anonymous surface ({@code GatewayRouteMatcher.isPublic}). Denying them here
 * would let a leftover cookie block the very requests that re-authenticate the
 * user or rotate the session. This does not widen access: a token issued by
 * {@code /api/auth/refresh} (or a fresh login) still carries the
 * {@code pwd_change_required} flag read from the database, so every other route
 * remains forbidden until the password changes.</p>
 *
 * <p>The check is shared by the reactive gateway and by every servlet service so
 * that the rule cannot drift.</p>
 */
public final class PasswordChangePolicy {

    static final String AUTH_PASSWORD_PATH = "/api/auth/password";

    static final String AUTH_LOGOUT_PATH = "/api/auth/logout";

    static final String AUTH_LOGIN_PATH = "/api/auth/login";

    static final String AUTH_REFRESH_PATH = "/api/auth/refresh";

    static final String AUTH_GUEST_PATH = "/api/auth/guest";

    static final String AUTH_ME_PATH = "/api/auth/me";

    static final String SITE_PATH = "/api/site";

    static final String SITE_ICON_PATH = "/api/site/icon";

    private PasswordChangePolicy() {
    }

    /**
     * Decides whether a restricted token may call the given route.
     *
     * @param httpMethod HTTP method, case-insensitive; may carry stray whitespace
     * @param path       request path, possibly including the context path, query
     *                   string, duplicate/trailing slashes; normalised before matching
     * @return {@code true} only for the allowlisted routes
     */
    public static boolean isAllowed(String httpMethod, String path) {
        String method = normalizeMethod(httpMethod);
        if (method == null) {
            return false;
        }
        String normalizedPath = normalizePath(path);
        if (normalizedPath == null) {
            return false;
        }
        return switch (method) {
            case "POST" -> normalizedPath.equals(AUTH_PASSWORD_PATH)
                    || normalizedPath.equals(AUTH_LOGOUT_PATH)
                    || normalizedPath.equals(AUTH_LOGIN_PATH)
                    || normalizedPath.equals(AUTH_REFRESH_PATH)
                    || normalizedPath.equals(AUTH_GUEST_PATH);
            case "GET" -> normalizedPath.equals(AUTH_ME_PATH)
                    || normalizedPath.equals(SITE_PATH)
                    || normalizedPath.equals(SITE_ICON_PATH);
            default -> false;
        };
    }

    private static String normalizeMethod(String httpMethod) {
        if (httpMethod == null || httpMethod.isBlank()) {
            return null;
        }
        return httpMethod.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Normalises a request path: drops query/fragment, collapses duplicate
     * slashes, guarantees a leading slash and strips a single trailing slash.
     */
    static String normalizePath(String path) {
        if (path == null) {
            return null;
        }
        String value = path.trim();
        if (value.isEmpty()) {
            return null;
        }
        int query = value.indexOf('?');
        if (query >= 0) {
            value = value.substring(0, query);
        }
        int fragment = value.indexOf('#');
        if (fragment >= 0) {
            value = value.substring(0, fragment);
        }
        value = value.replace('\\', '/');
        if (!value.startsWith("/")) {
            value = "/" + value;
        }
        value = value.replaceAll("/{2,}", "/");
        while (value.length() > 1 && value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
