package ru.nstu.system.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Tests for the restricted-token allowlist, including method awareness and path
 * normalisation.
 */
class PasswordChangePolicyTest {

    @ParameterizedTest
    @CsvSource({
            "POST, /api/auth/password",
            "POST, /api/auth/password/",
            "POST, //api//auth//password/",
            "POST, /api/auth/password?x=1",
            "post, /api/auth/password",
            "POST, /api/auth/logout",
            "GET, /api/auth/me",
            "GET, /api/auth/me/",
            "GET, /api/auth/me#fragment",
            "get, /api/auth/me",
            "GET, /api/site",
            "GET, /api/site/",
            "GET, /api/site?lang=ru",
            "get, /api/site",
    })
    void allowsExactlyTheAllowlistedRoutes(String method, String path) {
        assertThat(PasswordChangePolicy.isAllowed(method, path)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "GET, /api/auth/password",
            "PUT, /api/auth/password",
            "POST, /api/auth/me",
            "POST, /api/auth/logout/extra",
            "GET, /api/events",
            "POST, /api/events",
            "DELETE, /api/events/42",
            "GET, /api/users",
            "POST, /api/site",
            "PUT, /api/site",
            "POST, /api/admin/site",
            "GET, /api/auth/password/change",
            "PATCH, /api/auth/me",
    })
    void rejectsEverythingElse(String method, String path) {
        assertThat(PasswordChangePolicy.isAllowed(method, path)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "POST,",
            ",/api/auth/password",
            "GET,/",
            "GET,/unknown",
    })
    void rejectsMissingMethodOrUnknownPath(String method, String path) {
        String safeMethod = (method == null || method.isBlank()) ? null : method;
        String safePath = (path == null || path.isBlank()) ? null : path;
        assertThat(PasswordChangePolicy.isAllowed(safeMethod, safePath)).isFalse();
    }
}
