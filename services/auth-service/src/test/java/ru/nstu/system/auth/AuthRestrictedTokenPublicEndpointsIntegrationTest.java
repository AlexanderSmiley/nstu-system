package ru.nstu.system.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import ru.nstu.system.security.ParsedToken;

/**
 * Regression for the restricted-token allowlist: while a mandatory password
 * change is pending the public session entry points must stay reachable.
 *
 * <p>A browser keeps the {@code access_token} cookie issued with
 * {@code pwd_change_required=true} until the password is actually changed. If
 * {@code POST /api/auth/login} or {@code POST /api/auth/refresh} were denied with
 * {@code 403}, that leftover cookie would block re-authentication and session
 * rotation entirely. The routes outside the allowlist must remain forbidden, and
 * a refreshed token must still carry the flag so access is not widened.</p>
 *
 * <p>Fails before {@code PasswordChangePolicy} allowlists the three public
 * endpoints and passes afterwards.</p>
 */
class AuthRestrictedTokenPublicEndpointsIntegrationTest extends AbstractAuthIntegrationTest {

    @Test
    void restrictedTokenKeepsLoginRefreshAndGuestReachableButBlocksEverythingElse() throws Exception {
        // The bootstrap administrator is stored with must_change_password = true,
        // so this login yields a restricted access token.
        MvcResult login = login(ADMIN_USERNAME, ADMIN_PASSWORD);
        assertThat(login.getResponse().getStatus()).isEqualTo(200);
        String restrictedAccess = cookieValue(login, ACCESS_COOKIE);
        String refresh = cookieValue(login, REFRESH_COOKIE);
        assertThat(restrictedAccess).isNotNull();
        assertThat(refresh).isNotNull();
        assertThat(accessTokenParser.parse(restrictedAccess).passwordChangeRequired()).isTrue();

        // POST /api/auth/login with the restricted cookie left over from the previous
        // session must still succeed instead of returning 403.
        MvcResult relogin = mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/login")
                        .cookie(new Cookie(ACCESS_COOKIE, restrictedAccess))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", ADMIN_USERNAME, "password", ADMIN_PASSWORD))))
                .andReturn();
        assertThat(relogin.getResponse().getStatus()).isEqualTo(200);

        // POST /api/auth/refresh must rotate the session even with the restricted
        // access cookie present, and the new token must still be restricted.
        MvcResult refreshed = mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/refresh")
                        .cookie(new Cookie(ACCESS_COOKIE, restrictedAccess))
                        .cookie(new Cookie(REFRESH_COOKIE, refresh)))
                .andReturn();
        assertThat(refreshed.getResponse().getStatus()).isEqualTo(200);
        String rotatedAccess = cookieValue(refreshed, ACCESS_COOKIE);
        assertThat(rotatedAccess).isNotNull();
        assertThat(accessTokenParser.parse(rotatedAccess).passwordChangeRequired()).isTrue();

        // POST /api/auth/guest is likewise part of the public surface.
        MvcResult guest = mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/guest")
                        .cookie(new Cookie(ACCESS_COOKIE, restrictedAccess)))
                .andReturn();
        assertThat(guest.getResponse().getStatus()).isEqualTo(200);

        // A route outside the allowlist stays forbidden for the restricted token.
        MvcResult forbidden = mockMvc.perform(get("/api/users")
                        .cookie(new Cookie(ACCESS_COOKIE, restrictedAccess)))
                .andReturn();
        assertThat(forbidden.getResponse().getStatus()).isEqualTo(403);
    }
}
