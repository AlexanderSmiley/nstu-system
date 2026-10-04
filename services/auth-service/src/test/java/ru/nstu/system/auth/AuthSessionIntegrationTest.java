package ru.nstu.system.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import ru.nstu.system.auth.domain.Account;
import ru.nstu.system.auth.domain.Role;

/** Tasks 5.3 and 5.4: logout, refresh revocation and the {@code /me} profile. */
class AuthSessionIntegrationTest extends AbstractAuthIntegrationTest {

    @Test
    void meReturnsSubjectRolesDisplayNameAndPasswordChangeFlag() throws Exception {
        MvcResult login = login(ADMIN_USERNAME, ADMIN_PASSWORD);
        String access = cookieValue(login, ACCESS_COOKIE);

        MvcResult me = mockMvc.perform(get("/api/auth/me")
                        .cookie(new Cookie(ACCESS_COOKIE, access)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(me.getResponse().getContentAsString());
        assertThat(body.get("subject").asText()).isEqualTo(adminId().toString());
        assertThat(body.get("roles")).hasSize(1);
        assertThat(body.get("roles").get(0).asText()).isEqualTo("ADMIN");
        assertThat(body.get("displayName").asText()).isEqualTo("admin");
        assertThat(body.get("mustChangePassword").asBoolean()).isTrue();
        assertThat(body.get("guest").asBoolean()).isFalse();
    }

    @Test
    void meReturnsUsernameEmailAndPrimaryRoleFromAccount() throws Exception {
        accountRepository.save(Account.create(
                "staff-mail", "staff-mail", passwordEncoder.encode(FIXTURE_PASSWORD),
                "Staff Mail", "staff@nstu.ru", Role.STAFF, false, false));

        MvcResult login = login("staff-mail", FIXTURE_PASSWORD);
        String access = cookieValue(login, ACCESS_COOKIE);

        MvcResult me = mockMvc.perform(get("/api/auth/me")
                        .cookie(new Cookie(ACCESS_COOKIE, access)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(me.getResponse().getContentAsString());
        assertThat(body.get("username").asText()).isEqualTo("staff-mail");
        assertThat(body.get("email").asText()).isEqualTo("staff@nstu.ru");
        assertThat(body.get("role").asText()).isEqualTo("STAFF");

        // Backward compatibility: the legacy fields are still present.
        assertThat(body.get("subject").asText()).isEqualTo(
                accountRepository.findByUsernameNormalized("staff-mail").orElseThrow().getId().toString());
        assertThat(body.get("roles")).hasSize(1);
        assertThat(body.get("roles").get(0).asText()).isEqualTo("STAFF");
        assertThat(body.get("displayName").asText()).isEqualTo("Staff Mail");
        assertThat(body.get("mustChangePassword").asBoolean()).isFalse();
        assertThat(body.get("guest").asBoolean()).isFalse();
    }

    @Test
    void meWithoutAccessTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesRefreshTokenAndClearsCookies() throws Exception {
        MvcResult login = login(ADMIN_USERNAME, ADMIN_PASSWORD);
        String access = cookieValue(login, ACCESS_COOKIE);
        String refreshToken = cookieValue(login, REFRESH_COOKIE);

        MvcResult logout = mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie(ACCESS_COOKIE, access), new Cookie(REFRESH_COOKIE, refreshToken)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(setCookieHeader(logout, ACCESS_COOKIE)).contains("Max-Age=0");
        assertThat(setCookieHeader(logout, REFRESH_COOKIE)).contains("Max-Age=0");
        assertThat(refresh(refreshToken).getResponse().getStatus()).isEqualTo(401);
    }
}
