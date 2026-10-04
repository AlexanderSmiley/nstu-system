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
            "POST, /api/auth/login",
            "POST, /api/auth/login/",
            "POST, //api//auth//login/",
            "POST, /api/auth/login?redirect=/events",
            "POST, /api/auth/login#fragment",
            "post, /api/auth/login",
            "POST, /api/auth/refresh",
            "POST, /api/auth/refresh/",
            "POST, //api//auth//refresh/",
            "POST, /api/auth/refresh?x=1",
            "POST, /api/auth/refresh#fragment",
            "post, /api/auth/refresh",
            "POST, /api/auth/guest",
            "POST, /api/auth/guest/",
            "POST, //api//auth//guest/",
            "POST, /api/auth/guest?x=1",
            "POST, /api/auth/guest#fragment",
            "post, /api/auth/guest",
            "GET, /api/auth/me",
            "GET, /api/auth/me/",
            "GET, /api/auth/me#fragment",
            "get, /api/auth/me",
            "GET, /api/site",
            "GET, /api/site/",
            "GET, /api/site?lang=ru",
            "get, /api/site",
            "GET, /api/site/icon",
            "GET, /api/site/icon/",
            "GET, /api/site/icon?v=2",
            "get, /api/site/icon",
    })
    void allowsExactlyTheAllowlistedRoutes(String method, String path) {
        assertThat(PasswordChangePolicy.isAllowed(method, path)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "GET, /api/auth/password",
            "PUT, /api/auth/password",
            "GET, /api/auth/login",
            "PUT, /api/auth/login",
            "DELETE, /api/auth/login",
            "GET, /api/auth/refresh",
            "PUT, /api/auth/refresh",
            "GET, /api/auth/guest",
            "PUT, /api/auth/guest",
            "POST, /api/auth/me",
            "POST, /api/auth/logout/extra",
            "GET, /api/events",
            "POST, /api/events",
            "DELETE, /api/events/42",
            "GET, /api/users",
            "POST, /api/site",
            "PUT, /api/site",
            "PUT, /api/site/icon",
            "DELETE, /api/site/icon",
            "POST, /api/admin/site",
            "POST, /api/admin/site/icon",
            "PUT, /api/admin/site/icon",
            "DELETE, /api/admin/site/icon",
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
