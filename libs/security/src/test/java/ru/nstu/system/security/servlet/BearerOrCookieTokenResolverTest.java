package ru.nstu.system.security.servlet;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/** Unit tests for {@link BearerOrCookieTokenResolver}. */
class BearerOrCookieTokenResolverTest {

    @Test
    void returnsTokenFromAccessCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("access_token", "cookie-token"));

        assertThat(BearerOrCookieTokenResolver.resolve(request)).contains("cookie-token");
    }

    @Test
    void fallsBackToBearerHeaderWhenCookieIsAbsent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer header-token");

        assertThat(BearerOrCookieTokenResolver.resolve(request)).contains("header-token");
    }

    @Test
    void cookieTakesPrecedenceOverBearerHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("access_token", "cookie-token"));
        request.addHeader("Authorization", "Bearer header-token");

        assertThat(BearerOrCookieTokenResolver.resolve(request)).contains("cookie-token");
    }

    @Test
    void returnsEmptyWhenNoTokenIsPresent() {
        assertThat(BearerOrCookieTokenResolver.resolve(new MockHttpServletRequest())).isEmpty();
        assertThat(BearerOrCookieTokenResolver.resolve(null)).isEmpty();
    }

    @Test
    void ignoresBlankCookieValueAndMalformedHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("access_token", "  "));
        request.addHeader("Authorization", "Token abc");

        assertThat(BearerOrCookieTokenResolver.resolve(request)).isEmpty();
    }
}
