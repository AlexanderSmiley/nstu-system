package ru.nstu.system.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import ru.nstu.system.security.ParsedToken;

/**
 * Tasks 5.4 and 5.5: restricted mode while a password change is pending and the
 * password-change endpoint itself.
 */
class AuthPasswordChangeIntegrationTest extends AbstractAuthIntegrationTest {

    @Test
    void restrictedTokenIsBlockedEverywhereExceptMeLogoutAndPassword() throws Exception {
        MvcResult login = login(ADMIN_USERNAME, ADMIN_PASSWORD);
        String restrictedAccess = cookieValue(login, ACCESS_COOKIE);

        MvcResult forbidden = mockMvc.perform(post("/api/events")
                        .cookie(new Cookie(ACCESS_COOKIE, restrictedAccess)))
                .andReturn();
        assertThat(forbidden.getResponse().getStatus()).isEqualTo(403);

        mockMvc.perform(get("/api/auth/me").cookie(new Cookie(ACCESS_COOKIE, restrictedAccess)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/logout").cookie(new Cookie(ACCESS_COOKIE, restrictedAccess)))
                .andExpect(status().isOk());
    }

    @Test
    void successfulPasswordChangeClearsFlagRevokesRefreshAndIssuesFullToken() throws Exception {
        MvcResult login = login(ADMIN_USERNAME, ADMIN_PASSWORD);
        String restrictedAccess = cookieValue(login, ACCESS_COOKIE);
        String oldRefresh = cookieValue(login, REFRESH_COOKIE);

        MvcResult changed = mockMvc.perform(withAccessCookie(post("/api/auth/password"), restrictedAccess)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("oldPassword", ADMIN_PASSWORD, "newPassword", "NewPass123"))))
                .andReturn();

        assertThat(changed.getResponse().getStatus()).isEqualTo(200);
        ParsedToken newAccess = accessTokenParser.parse(cookieValue(changed, ACCESS_COOKIE));
        assertThat(newAccess.passwordChangeRequired()).isFalse();
        assertThat(cookieValue(changed, REFRESH_COOKIE)).isNotEqualTo(oldRefresh);

        assertThat(accountRepository.findById(adminId()).orElseThrow().isMustChangePassword()).isFalse();
        assertThat(refreshTokenRepository.findByTokenHash(refreshTokenGenerator.hash(oldRefresh))
                .orElseThrow().isRevoked()).isTrue();
        assertThat(refresh(oldRefresh).getResponse().getStatus()).isEqualTo(401);

        MvcResult me = mockMvc.perform(get("/api/auth/me")
                        .cookie(new Cookie(ACCESS_COOKIE, cookieValue(changed, ACCESS_COOKIE))))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(me.getResponse().getContentAsString())
                .get("mustChangePassword").asBoolean()).isFalse();
    }

    @ParameterizedTest(name = "rejects new password [{0}]")
    @ValueSource(strings = {"short1", "12345678", "abcdefgh", "Admin-Pass1"})
    void rejectsNewPasswordsThatViolateThePolicy(String newPassword) throws Exception {
        MvcResult login = login(ADMIN_USERNAME, ADMIN_PASSWORD);
        String access = cookieValue(login, ACCESS_COOKIE);

        MvcResult result = mockMvc.perform(withAccessCookie(post("/api/auth/password"), access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("oldPassword", ADMIN_PASSWORD, "newPassword", newPassword))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("weak_password");
        assertThat(accountRepository.findById(adminId()).orElseThrow().isMustChangePassword()).isTrue();
    }

    @Test
    void wrongOldPasswordIsRejected() throws Exception {
        MvcResult login = login(ADMIN_USERNAME, ADMIN_PASSWORD);
        String access = cookieValue(login, ACCESS_COOKIE);

        MvcResult result = mockMvc.perform(withAccessCookie(post("/api/auth/password"), access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("oldPassword", "Wrong-Old1", "newPassword", "NewPass123"))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("invalid_old_password");
        assertThat(accountRepository.findById(adminId()).orElseThrow().isMustChangePassword()).isTrue();
    }

    @Test
    void guestCannotChangePassword() throws Exception {
        MvcResult guest = mockMvc.perform(post("/api/auth/guest")).andReturn();
        String access = cookieValue(guest, ACCESS_COOKIE);

        mockMvc.perform(withAccessCookie(post("/api/auth/password"), access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("oldPassword", "whatever1", "newPassword", "NewPass123"))))
                .andExpect(status().isForbidden());
    }
}
