package ru.nstu.system.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import ru.nstu.system.auth.domain.Account;
import ru.nstu.system.auth.domain.Role;
import ru.nstu.system.security.ParsedToken;

/** Task 5.1: password login, cookie issuance and credential failure handling. */
class AuthLoginIntegrationTest extends AbstractAuthIntegrationTest {

    @Test
    void successfulLoginSetsAccessAndRefreshCookiesAndReturnsProfile() throws Exception {
        MvcResult result = login(ADMIN_USERNAME, ADMIN_PASSWORD);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("username").asText()).isEqualTo("admin");
        assertThat(body.get("role").asText()).isEqualTo("ADMIN");
        assertThat(body.get("mustChangePassword").asBoolean()).isTrue();

        String accessHeader = setCookieHeader(result, ACCESS_COOKIE);
        assertThat(accessHeader)
                .as("access cookie attributes")
                .isNotNull()
                .contains("HttpOnly")
                .contains("SameSite=Lax")
                .contains("Path=/")
                .contains("Max-Age=900");

        String refreshHeader = setCookieHeader(result, REFRESH_COOKIE);
        assertThat(refreshHeader)
                .as("refresh cookie attributes")
                .isNotNull()
                .contains("HttpOnly")
                .contains("SameSite=Lax")
                .contains("Path=/")
                .contains("Max-Age=604800");

        ParsedToken token = accessTokenParser.parse(cookieValue(result, ACCESS_COOKIE));
        assertThat(token.subject()).isEqualTo(adminId().toString());
        assertThat(token.roles()).containsExactly("ADMIN");
        assertThat(token.passwordChangeRequired()).isTrue();
    }

    @Test
    void successfulLoginReturnsAllProfileFields() throws Exception {
        accountRepository.save(Account.create(
                "staff-mail", "staff-mail", passwordEncoder.encode(FIXTURE_PASSWORD),
                "Staff Mail", "staff@nstu.ru", Role.STAFF, false, false));

        MvcResult result = login("staff-mail", FIXTURE_PASSWORD);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("id").asText()).isNotEmpty();
        assertThat(body.get("username").asText()).isEqualTo("staff-mail");
        assertThat(body.get("displayName").asText()).isEqualTo("Staff Mail");
        assertThat(body.get("role").asText()).isEqualTo("STAFF");
        assertThat(body.get("mustChangePassword").asBoolean()).isFalse();
        assertThat(body.get("email").asText()).isEqualTo("staff@nstu.ru");
    }

    @Test
    void invalidPasswordReturns401WithGenericBody() throws Exception {
        MvcResult result = login(ADMIN_USERNAME, "definitely-wrong");

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("error").asText()).isEqualTo("invalid_credentials");
        assertThat(body.get("message").asText()).isEqualTo("Неверный логин или пароль");
        assertThat(result.getResponse().getHeaders("Set-Cookie")).isEmpty();
    }

    @Test
    void unknownUsernameReturns401WithBodyIdenticalToWrongPassword() throws Exception {
        MvcResult unknown = login("no-such-user", "whatever-1");
        MvcResult wrongPassword = login(ADMIN_USERNAME, "definitely-wrong");

        assertThat(unknown.getResponse().getStatus()).isEqualTo(401);
        assertThat(wrongPassword.getResponse().getStatus()).isEqualTo(401);
        assertThat(unknown.getResponse().getContentAsString())
                .isEqualTo(wrongPassword.getResponse().getContentAsString());
        assertThat(unknown.getResponse().getHeaders("Set-Cookie")).isEmpty();
    }

    @Test
    void blockedAccountReturns403WithoutSession() throws Exception {
        accountRepository.save(Account.create(
                "blocked-staff", "blocked-staff", passwordEncoder.encode("Staff-Pass1"),
                "Blocked Staff", null, Role.STAFF, true, false));

        MvcResult result = login("blocked-staff", "Staff-Pass1");

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(result.getResponse().getHeaders("Set-Cookie")).isEmpty();
    }
}
