package ru.nstu.system.security;

import java.util.Locale;

/**
 * Allowlist for the restricted access token (design.md D7).
 *
 * <p>While a mandatory password change is pending the only reachable routes are
 * {@code POST /api/auth/password}, {@code POST /api/auth/logout},
 * {@code GET /api/auth/me} and the public {@code GET /api/site} (the
 * change-password screen displays the site name); everything else is denied. The
 * check is shared by the reactive gateway and by every servlet service so that
 * the rule cannot drift.</p>
 */
public final class PasswordChangePolicy {

    static final String AUTH_PASSWORD_PATH = "/api/auth/password";

    static final String AUTH_LOGOUT_PATH = "/api/auth/logout";

    static final String AUTH_ME_PATH = "/api/auth/me";

    static final String SITE_PATH = "/api/site";

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
                    || normalizedPath.equals(AUTH_LOGOUT_PATH);
            case "GET" -> normalizedPath.equals(AUTH_ME_PATH)
                    || normalizedPath.equals(SITE_PATH);
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
