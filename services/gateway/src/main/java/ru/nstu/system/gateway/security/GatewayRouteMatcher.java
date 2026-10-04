package ru.nstu.system.gateway.security;

import java.util.Locale;

/**
 * Pure path/method classification used by the reactive authorization filter.
 *
 * <p>Two decisions are centralised here so they can be unit tested without
 * starting a server:</p>
 * <ul>
 *   <li>{@link #isInternal(String)} — the {@code /internal/**} tree is never
 *       reachable from outside (design.md D12, task 4.4). It answers {@code 404}
 *       instead of {@code 401/403} so the existence of internal endpoints is not
 *       confirmed.</li>
 *   <li>{@link #isPublic(String, String)} — the only anonymous API surface:
 *       login, refresh, guest session and the public site name (design.md D27).</li>
 * </ul>
 *
 * <p>Paths are normalised before matching (query/fragment dropped, backslashes
 * converted, duplicate slashes collapsed, leading slash guaranteed, a single
 * trailing slash removed) so that a request cannot dodge the rules with
 * {@code //api/auth/login} or a trailing slash.</p>
 */
public final class GatewayRouteMatcher {

    private static final String AUTH_LOGIN = "/api/auth/login";

    private static final String AUTH_REFRESH = "/api/auth/refresh";

    private static final String AUTH_GUEST = "/api/auth/guest";

    private static final String SITE = "/api/site";

    private static final String INTERNAL_PREFIX = "/internal";

    private GatewayRouteMatcher() {
    }

    /**
     * @param method HTTP method, case-insensitive; may be {@code null}
     * @param path   request path, possibly with query string or duplicate slashes
     * @return {@code true} when the route may be called without an access token
     */
    public static boolean isPublic(String method, String path) {
        String normalizedMethod = normalizeMethod(method);
        String normalizedPath = normalizePath(path);
        if (normalizedMethod == null || normalizedPath == null) {
            return false;
        }
        return switch (normalizedMethod) {
            case "POST" -> normalizedPath.equals(AUTH_LOGIN)
                    || normalizedPath.equals(AUTH_REFRESH)
                    || normalizedPath.equals(AUTH_GUEST);
            case "GET" -> normalizedPath.equals(SITE);
            default -> false;
        };
    }

    /**
     * @param path request path
     * @return {@code true} when the path belongs to the internal namespace
     */
    public static boolean isInternal(String path) {
        String normalizedPath = normalizePath(path);
        return normalizedPath != null && normalizedPath.startsWith(INTERNAL_PREFIX);
    }

    private static String normalizeMethod(String method) {
        if (method == null || method.isBlank()) {
            return null;
        }
        return method.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Normalises a request path so that equivalent spellings compare equal.
     * Mirrors the normalisation performed by the shared
     * {@code ru.nstu.system.security.PasswordChangePolicy}.
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
