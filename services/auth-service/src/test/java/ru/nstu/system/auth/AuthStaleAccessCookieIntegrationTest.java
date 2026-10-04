package ru.nstu.system.auth;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import ru.nstu.system.security.JwtProperties;
import ru.nstu.system.security.TokenIssuer;

/**
 * D1 regression: a stale {@code access_token} cookie (expired or garbage) must not
 * make the public auth endpoints fail with {@code 401}. The browser routinely
 * reaches {@code POST /api/auth/refresh} with an expired access cookie, and
 * {@code POST /api/auth/login} with whatever cookie is left over from a previous
 * session.
 */
class AuthStaleAccessCookieIntegrationTest extends AbstractAuthIntegrationTest {

    @Autowired
    private JwtProperties jwtProperties;

    @Test
    void refreshSucceedsWithValidRefreshCookieAndGarbageAccessCookie() throws Exception {
        MvcResult login = login(ADMIN_USERNAME, ADMIN_PASSWORD);
        String refreshToken = cookieValue(login, REFRESH_COOKIE);

        MvcResult refreshed = mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/refresh")
                        .cookie(new Cookie(REFRESH_COOKIE, refreshToken))
                        .cookie(new Cookie(ACCESS_COOKIE, "not-a-jwt")))
                .andReturn();

        assertThat(refreshed.getResponse().getStatus()).isEqualTo(200);
        assertThat(setCookieHeader(refreshed, ACCESS_COOKIE)).isNotNull();
    }

    @Test
    void refreshSucceedsWithValidRefreshCookieAndExpiredAccessCookie() throws Exception {
        MvcResult login = login(ADMIN_USERNAME, ADMIN_PASSWORD);
        String refreshToken = cookieValue(login, REFRESH_COOKIE);

        MvcResult refreshed = mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/refresh")
                        .cookie(new Cookie(REFRESH_COOKIE, refreshToken))
                        .cookie(new Cookie(ACCESS_COOKIE, expiredAccessToken())))
                .andReturn();

        assertThat(refreshed.getResponse().getStatus()).isEqualTo(200);
        assertThat(setCookieHeader(refreshed, ACCESS_COOKIE)).isNotNull();
    }

    @Test
    void loginSucceedsWithGarbageAccessCookie() throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/login")
                        .cookie(new Cookie(ACCESS_COOKIE, "not-a-jwt"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(java.util.Map.of(
                                "username", ADMIN_USERNAME, "password", ADMIN_PASSWORD))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(setCookieHeader(result, ACCESS_COOKIE)).isNotNull();
    }

    private String expiredAccessToken() {
        Clock past = Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneOffset.UTC);
        return new TokenIssuer(jwtProperties, past)
                .issueAccessToken(adminId().toString(), Set.of("ADMIN"), true);
    }
}
