package ru.nstu.system.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ru.nstu.system.auth.domain.Account;
import ru.nstu.system.auth.domain.Role;
import ru.nstu.system.auth.service.UserManagementService;
import ru.nstu.system.auth.web.ApiException;

/**
 * Integration tests for administrator block / unblock / password reset
 * (identity spec "Блокировка и разблокировка пользователя" and "Сброс пароля
 * администратором", task 5.10).
 *
 * <p>Each successful mutation is asserted end to end: the account row, the revoked
 * refresh tokens, the published outbox event and the observable effect on
 * {@code POST /api/auth/login} / {@code POST /api/auth/refresh}.</p>
 */
class UserAdministrationIntegrationTest extends AbstractAuthIntegrationTest {

    @Autowired
    private UserManagementService userManagementService;

    @BeforeEach
    void clearOutbox() {
        jdbcTemplate.update("delete from auth.outbox");
    }

    // --- block -----------------------------------------------------------------

    @Test
    void blockRevokesSessionsAndPublishesEvent() throws Exception {
        String admin = adminAccess();
        Account staff = newAccount("block-me", Role.STAFF);
        MvcResult staffLogin = login("block-me", FIXTURE_PASSWORD);
        assertThat(staffLogin.getResponse().getStatus()).isEqualTo(200);
        String staffRefresh = cookieValue(staffLogin, REFRESH_COOKIE);
        assertThat(staffRefresh).isNotBlank();

        MvcResult result = block(admin, staff.getId());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(asJson(result).get("blocked").asBoolean()).isTrue();
        assertThat(accountRepository.findById(staff.getId()).orElseThrow().isBlocked()).isTrue();

        JsonNode event = outboxEvent("account.blocked");
        assertThat(event.get("eventType").asText()).isEqualTo("account.blocked");
        assertThat(event.get("payload").get("accountId").asText()).isEqualTo(staff.getId().toString());

        assertThat(login("block-me", FIXTURE_PASSWORD).getResponse().getStatus()).isEqualTo(403);
        assertThat(refresh(staffRefresh).getResponse().getStatus()).isEqualTo(401);
        assertThat(refreshTokenRepository.countByAccountIdAndRevokedFalse(staff.getId())).isZero();
    }

    @Test
    void blockingTwiceIsIdempotentAndWritesOneEvent() throws Exception {
        String admin = adminAccess();
        Account staff = newAccount("block-twice", Role.STAFF);

        MvcResult first = block(admin, staff.getId());
        MvcResult second = block(admin, staff.getId());

        assertThat(first.getResponse().getStatus()).isEqualTo(200);
        assertThat(second.getResponse().getStatus()).isEqualTo(200);
        assertThat(asJson(second).get("blocked").asBoolean()).isTrue();
        assertThat(accountRepository.findById(staff.getId()).orElseThrow().isBlocked()).isTrue();
        assertThat(outboxCount("account.blocked")).isEqualTo(1);
    }

    // --- unblock ---------------------------------------------------------------

    @Test
    void unblockRestoresLoginAndPublishesEvent() throws Exception {
        String admin = adminAccess();
        Account staff = newAccount("unblock-me", Role.STAFF);
        assertThat(block(admin, staff.getId()).getResponse().getStatus()).isEqualTo(200);
        assertThat(login("unblock-me", FIXTURE_PASSWORD).getResponse().getStatus()).isEqualTo(403);

        MvcResult result = unblock(admin, staff.getId());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(asJson(result).get("blocked").asBoolean()).isFalse();
        assertThat(accountRepository.findById(staff.getId()).orElseThrow().isBlocked()).isFalse();

        JsonNode event = outboxEvent("account.unblocked");
        assertThat(event.get("eventType").asText()).isEqualTo("account.unblocked");
        assertThat(event.get("payload").get("accountId").asText()).isEqualTo(staff.getId().toString());

        assertThat(login("unblock-me", FIXTURE_PASSWORD).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void unblockingTwiceIsIdempotentAndWritesOneEvent() throws Exception {
        String admin = adminAccess();
        Account staff = newAccount("unblock-twice", Role.STAFF);
        block(admin, staff.getId());

        MvcResult first = unblock(admin, staff.getId());
        MvcResult second = unblock(admin, staff.getId());

        assertThat(first.getResponse().getStatus()).isEqualTo(200);
        assertThat(second.getResponse().getStatus()).isEqualTo(200);
        assertThat(accountRepository.findById(staff.getId()).orElseThrow().isBlocked()).isFalse();
        assertThat(outboxCount("account.unblocked")).isEqualTo(1);
    }

    // --- password reset --------------------------------------------------------

    @Test
    void resetPasswordReturnsOneTimePasswordRevokesSessionsAndPublishesEvent() throws Exception {
        String admin = adminAccess();
        Account staff = newAccount("reset-me", Role.STAFF);
        MvcResult staffLogin = login("reset-me", FIXTURE_PASSWORD);
        String oldRefresh = cookieValue(staffLogin, REFRESH_COOKIE);
        assertThat(oldRefresh).isNotBlank();

        MvcResult result = resetPassword(admin, staff.getId());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = asJson(result);
        String temporaryPassword = body.get("temporaryPassword").asText();
        assertThat(temporaryPassword).hasSize(12);
        assertThat(body.get("mustChangePassword").asBoolean()).isTrue();

        Account updated = accountRepository.findById(staff.getId()).orElseThrow();
        assertThat(updated.getPasswordHash()).startsWith("$2").isNotEqualTo(temporaryPassword);
        assertThat(passwordEncoder.matches(temporaryPassword, updated.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches(FIXTURE_PASSWORD, updated.getPasswordHash())).isFalse();
        assertThat(updated.isMustChangePassword()).isTrue();

        JsonNode event = outboxEvent("account.password_reset");
        assertThat(event.get("eventType").asText()).isEqualTo("account.password_reset");
        assertThat(event.get("payload").get("accountId").asText()).isEqualTo(staff.getId().toString());

        // The one-time password never resurfaces in a read endpoint.
        MvcResult list = mockMvc.perform(withAccessCookie(get("/api/users"), admin)).andReturn();
        assertThat(list.getResponse().getStatus()).isEqualTo(200);
        assertThat(list.getResponse().getContentAsString()).doesNotContain(temporaryPassword);
        assertThat(list.getResponse().getContentAsString()).doesNotContain(updated.getPasswordHash());

        assertThat(refresh(oldRefresh).getResponse().getStatus()).isEqualTo(401);
        assertThat(refreshTokenRepository.countByAccountIdAndRevokedFalse(staff.getId())).isZero();
    }

    // --- guards ----------------------------------------------------------------

    @Test
    void selfBlockIsRejectedWithBadRequest() throws Exception {
        String admin = adminAccess();

        MvcResult result = block(admin, adminId());

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(asJson(result).get("error").asText()).isEqualTo("self_block_forbidden");
        assertThat(accountRepository.findById(adminId()).orElseThrow().isBlocked()).isFalse();
        assertThat(outboxCount("account.blocked")).isZero();
    }

    @Test
    void blockingTheLastAdministratorIsRejectedEvenForAnotherActor() {
        Account staff = newAccount("staff-actor", Role.STAFF);

        assertThatThrownBy(() -> userManagementService.block(staff.getId(), adminId()))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> {
                    ApiException apiException = (ApiException) exception;
                    assertThat(apiException.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(apiException.getCode()).isEqualTo("last_admin_required");
                });
        assertThat(accountRepository.findById(adminId()).orElseThrow().isBlocked()).isFalse();
        assertThat(outboxCount("account.blocked")).isZero();
    }

    @Test
    void unknownUserReturnsNotFoundOnAllOperations() throws Exception {
        String admin = adminAccess();
        UUID unknown = UUID.randomUUID();

        MvcResult block = block(admin, unknown);
        MvcResult unblock = unblock(admin, unknown);
        MvcResult reset = resetPassword(admin, unknown);

        assertThat(block.getResponse().getStatus()).isEqualTo(404);
        assertThat(unblock.getResponse().getStatus()).isEqualTo(404);
        assertThat(reset.getResponse().getStatus()).isEqualTo(404);
        assertThat(asJson(block).get("error").asText()).isEqualTo("user_not_found");
        assertThat(outboxCount("account.blocked")).isZero();
        assertThat(outboxCount("account.unblocked")).isZero();
        assertThat(outboxCount("account.password_reset")).isZero();
    }

    // --- access control --------------------------------------------------------

    @Test
    void staffStudentAndGuestAreForbiddenOnAllAdministrativeActions() throws Exception {
        Account staff = newAccount("access-staff", Role.STAFF);
        newAccount("access-student", Role.STUDENT);
        String staffToken = cookieValue(login("access-staff", FIXTURE_PASSWORD), ACCESS_COOKIE);
        String studentToken = cookieValue(login("access-student", FIXTURE_PASSWORD), ACCESS_COOKIE);
        String guestToken = cookieValue(mockMvc.perform(post("/api/auth/guest")).andReturn(), ACCESS_COOKIE);

        for (String token : List.of(staffToken, studentToken, guestToken)) {
            assertForbidden(token, post("/api/users/" + staff.getId() + "/block"));
            assertForbidden(token, post("/api/users/" + staff.getId() + "/unblock"));
            assertForbidden(token, post("/api/users/" + staff.getId() + "/reset-password"));
        }
        assertThat(accountRepository.findById(staff.getId()).orElseThrow().isBlocked()).isFalse();
    }

    // --- helpers ---------------------------------------------------------------

    private MvcResult block(String adminToken, UUID targetId) throws Exception {
        return mockMvc.perform(withAccessCookie(
                post("/api/users/" + targetId + "/block"), adminToken)).andReturn();
    }

    private MvcResult unblock(String adminToken, UUID targetId) throws Exception {
        return mockMvc.perform(withAccessCookie(
                post("/api/users/" + targetId + "/unblock"), adminToken)).andReturn();
    }

    private MvcResult resetPassword(String adminToken, UUID targetId) throws Exception {
        return mockMvc.perform(withAccessCookie(
                post("/api/users/" + targetId + "/reset-password"), adminToken)).andReturn();
    }

    private void assertForbidden(String token, MockHttpServletRequestBuilder builder) throws Exception {
        MvcResult result = mockMvc.perform(withAccessCookie(builder, token)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    private JsonNode asJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
