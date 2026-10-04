package ru.nstu.system.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import ru.nstu.system.security.ParsedToken;

/** Task 5.6: anonymous guest sessions (design.md D11). */
class AuthGuestIntegrationTest extends AbstractAuthIntegrationTest {

    @Test
    void guestEndpointIssuesGuestTokenWithoutCreatingAnAccount() throws Exception {
        long accountsBefore = accountRepository.count();

        MvcResult guest = mockMvc.perform(post("/api/auth/guest"))
                .andExpect(status().isOk())
                .andReturn();

        String accessHeader = setCookieHeader(guest, ACCESS_COOKIE);
        assertThat(accessHeader)
                .isNotNull()
                .contains("HttpOnly")
                .contains("SameSite=Lax")
                .contains("Path=/")
                .contains("Max-Age=604800");
        assertThat(setCookieHeader(guest, REFRESH_COOKIE)).isNull();

        ParsedToken token = accessTokenParser.parse(cookieValue(guest, ACCESS_COOKIE));
        assertThat(token.subject()).startsWith("guest:");
        assertThat(token.roles()).containsExactly("GUEST");
        assertThat(token.passwordChangeRequired()).isFalse();
        Duration remaining = Duration.between(Instant.now(), token.expiresAt());
        assertThat(remaining)
                .isBetween(Duration.ofDays(7).minusMinutes(2), Duration.ofDays(7).plusMinutes(1));

        assertThat(accountRepository.count()).isEqualTo(accountsBefore);
    }

    @Test
    void guestSessionCanReadMeWithGuestRoleOnly() throws Exception {
        MvcResult guest = mockMvc.perform(post("/api/auth/guest")).andReturn();
        String access = cookieValue(guest, ACCESS_COOKIE);

        MvcResult me = mockMvc.perform(get("/api/auth/me")
                        .cookie(new Cookie(ACCESS_COOKIE, access)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(me.getResponse().getContentAsString());
        assertThat(body.get("guest").asBoolean()).isTrue();
        assertThat(body.get("roles")).hasSize(1);
        assertThat(body.get("roles").get(0).asText()).isEqualTo("GUEST");
        assertThat(body.get("mustChangePassword").asBoolean()).isFalse();
        assertThat(body.get("username").isNull()).isTrue();
        assertThat(body.get("email").isNull()).isTrue();
        assertThat(body.get("role").asText()).isEqualTo("GUEST");
    }

    @Test
    void guestEndpointReturnsNullUsernameEmailAndGuestRole() throws Exception {
        MvcResult guest = mockMvc.perform(post("/api/auth/guest"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(guest.getResponse().getContentAsString());
        assertThat(body.get("guest").asBoolean()).isTrue();
        assertThat(body.get("subject").asText()).startsWith("guest:");
        assertThat(body.get("role").asText()).isEqualTo("GUEST");
        assertThat(body.get("username").isNull()).isTrue();
        assertThat(body.get("email").isNull()).isTrue();

        // Backward compatibility: the legacy fields are still present.
        assertThat(body.get("roles")).hasSize(1);
        assertThat(body.get("roles").get(0).asText()).isEqualTo("GUEST");
        assertThat(body.get("displayName").isNull()).isTrue();
        assertThat(body.get("mustChangePassword").asBoolean()).isFalse();
    }
}
