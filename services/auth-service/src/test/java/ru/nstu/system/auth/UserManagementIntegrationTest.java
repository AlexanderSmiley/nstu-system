package ru.nstu.system.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ru.nstu.system.auth.domain.Account;
import ru.nstu.system.auth.domain.Role;

/**
 * Integration tests for administrator user management (tasks 5.8, 5.9, 5.11):
 * creation with a one-time temporary password, listing, editing, outbox events and
 * the ADMIN-only restriction.
 */
class UserManagementIntegrationTest extends AbstractAuthIntegrationTest {

    private static final String STAFF_USERNAME = "staff-user";
    private static final String STUDENT_USERNAME = "student-user";
    private static final String USER_PASSWORD = "Staff-Pass1";

    @BeforeEach
    void clearOutbox() {
        jdbcTemplate.update("delete from auth.outbox");
    }

    // --- creation (5.8) --------------------------------------------------------

    @Test
    void createReturnsTemporaryPasswordOnceAndWritesCreatedEvent() throws Exception {
        String admin = fullAdminAccess();

        MvcResult result = mockMvc.perform(withAccessCookie(post("/api/users"), admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", "student-1",
                                "displayName", "Иван Иванов",
                                "role", "STUDENT",
                                "email", "ivan@example.com"))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        JsonNode body = asJson(result);
        String temporaryPassword = body.get("temporaryPassword").asText();
        assertThat(temporaryPassword).hasSize(12);
        assertThat(body.get("mustChangePassword").asBoolean()).isTrue();
        UUID id = UUID.fromString(body.get("id").asText());

        Account account = accountRepository.findByUsernameNormalized("student-1").orElseThrow();
        assertThat(account.getPasswordHash()).startsWith("$2").isNotEqualTo(temporaryPassword);
        assertThat(passwordEncoder.matches(temporaryPassword, account.getPasswordHash())).isTrue();
        assertThat(account.isMustChangePassword()).isTrue();

        JsonNode event = objectMapper.readTree(String.valueOf(jdbcTemplate.queryForMap(
                "select payload::text as payload from auth.outbox where event_type = 'account.created'")
                .get("payload")));
        assertThat(event.get("eventType").asText()).isEqualTo("account.created");
        JsonNode payload = event.get("payload");
        assertThat(payload.get("accountId").asText()).isEqualTo(id.toString());
        assertThat(payload.get("username").asText()).isEqualTo("student-1");
        assertThat(payload.get("role").asText()).isEqualTo("STUDENT");
        assertThat(payload.get("displayName").asText()).isEqualTo("Иван Иванов");
    }

    @Test
    void listNeverReturnsPasswordMaterialAndExposesRequiredFields() throws Exception {
        String admin = fullAdminAccess();
        String temporaryPassword = createUser(admin, "student-2", "STUDENT").get("temporaryPassword").asText();

        MvcResult result = mockMvc.perform(withAccessCookie(get("/api/users"), admin)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String body = result.getResponse().getContentAsString();
        Account account = accountRepository.findByUsernameNormalized("student-2").orElseThrow();
        assertThat(body)
                .doesNotContain("temporaryPassword")
                .doesNotContain(temporaryPassword)
                .doesNotContain(account.getPasswordHash());

        JsonNode array = objectMapper.readTree(body);
        assertThat(array.isArray()).isTrue();
        assertThat(array).hasSize(2);
        JsonNode student = find(array, "student-2");
        assertThat(student.get("role").asText()).isEqualTo("STUDENT");
        assertThat(student.get("mustChangePassword").asBoolean()).isTrue();
        assertThat(student.get("blocked").asBoolean()).isFalse();
        assertThat(student.hasNonNull("createdAt")).isTrue();
        assertThat(student.hasNonNull("updatedAt")).isTrue();
    }

    @Test
    void duplicateUsernameIsRejectedRegardlessOfCase() throws Exception {
        String admin = fullAdminAccess();
        createUser(admin, "dup-user", "STAFF");

        MvcResult upperCase = createUserRequest(admin, "DUP-USER", "STAFF");
        MvcResult sameCase = createUserRequest(admin, "dup-user", "STAFF");

        assertThat(upperCase.getResponse().getStatus()).isEqualTo(409);
        assertThat(asJson(upperCase).get("error").asText()).isEqualTo("username_taken");
        assertThat(sameCase.getResponse().getStatus()).isEqualTo(409);
        assertThat(accountRepository.count()).isEqualTo(2);
    }

    @Test
    void adminAndGuestRolesCannotBeAssignedOnCreate() throws Exception {
        String admin = fullAdminAccess();

        MvcResult adminRole = createUserRequest(admin, "wannabe-admin", "ADMIN");
        MvcResult guestRole = createUserRequest(admin, "wannabe-guest", "GUEST");

        assertThat(adminRole.getResponse().getStatus()).isEqualTo(400);
        assertThat(asJson(adminRole).get("error").asText()).isEqualTo("admin_not_assignable");
        assertThat(guestRole.getResponse().getStatus()).isEqualTo(400);
        assertThat(asJson(guestRole).get("error").asText()).isEqualTo("guest_not_assignable");
        assertThat(accountRepository.count()).isEqualTo(1);
    }

    // --- editing (5.8) ---------------------------------------------------------

    @Test
    void patchUpdatesFieldsAndWritesUpdatedEvent() throws Exception {
        String admin = fullAdminAccess();
        UUID id = UUID.fromString(createUser(admin, "patch-me", "STAFF").get("id").asText());

        MvcResult result = mockMvc.perform(withAccessCookie(patch("/api/users/" + id), admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "displayName", "Новое Имя",
                                "email", "new@example.com",
                                "role", "STUDENT"))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = asJson(result);
        assertThat(body.get("displayName").asText()).isEqualTo("Новое Имя");
        assertThat(body.get("email").asText()).isEqualTo("new@example.com");
        assertThat(body.get("role").asText()).isEqualTo("STUDENT");
        assertThat(body.has("temporaryPassword")).isFalse();

        Account account = accountRepository.findById(id).orElseThrow();
        assertThat(account.getDisplayName()).isEqualTo("Новое Имя");
        assertThat(account.getRole()).isEqualTo(Role.STUDENT);

        JsonNode event = objectMapper.readTree(String.valueOf(jdbcTemplate.queryForMap(
                "select payload::text as payload from auth.outbox where event_type = 'account.updated'")
                .get("payload")));
        assertThat(event.get("eventType").asText()).isEqualTo("account.updated");
        JsonNode payload = event.get("payload");
        assertThat(payload.get("accountId").asText()).isEqualTo(id.toString());
        assertThat(payload.get("username").asText()).isEqualTo("patch-me");
        assertThat(payload.get("role").asText()).isEqualTo("STUDENT");
        assertThat(payload.get("displayName").asText()).isEqualTo("Новое Имя");
    }

    @Test
    void patchCannotPromoteToAdmin() throws Exception {
        String admin = fullAdminAccess();
        UUID id = UUID.fromString(createUser(admin, "promote-me", "STAFF").get("id").asText());

        MvcResult result = mockMvc.perform(withAccessCookie(patch("/api/users/" + id), admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("role", "ADMIN"))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(asJson(result).get("error").asText()).isEqualTo("admin_not_assignable");
        assertThat(accountRepository.findById(id).orElseThrow().getRole()).isEqualTo(Role.STAFF);
    }

    @Test
    void adminCannotDemoteThemselves() throws Exception {
        String admin = fullAdminAccess();

        MvcResult result = mockMvc.perform(withAccessCookie(patch("/api/users/" + adminId()), admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("role", "STUDENT"))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(asJson(result).get("error").asText()).isEqualTo("self_demotion_forbidden");
        assertThat(accountRepository.findById(adminId()).orElseThrow().getRole()).isEqualTo(Role.ADMIN);
    }

    // --- access control (5.11) -------------------------------------------------

    @Test
    void staffStudentAndGuestAreForbiddenOnAllUserEndpoints() throws Exception {
        Account staff = createAccount(STAFF_USERNAME, Role.STAFF);
        createAccount(STUDENT_USERNAME, Role.STUDENT);
        String staffToken = cookieValue(login(STAFF_USERNAME, USER_PASSWORD), ACCESS_COOKIE);
        String studentToken = cookieValue(login(STUDENT_USERNAME, USER_PASSWORD), ACCESS_COOKIE);
        String guestToken = cookieValue(mockMvc.perform(post("/api/auth/guest")).andReturn(), ACCESS_COOKIE);

        for (String token : List.of(staffToken, studentToken, guestToken)) {
            assertForbidden(token, get("/api/users"));
            assertForbidden(token, post("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("username", "x", "displayName", "x", "role", "STUDENT"))));
            assertForbidden(token, patch("/api/users/" + staff.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("displayName", "x"))));
        }
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/users")).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }

    // --- helpers ---------------------------------------------------------------

    private String fullAdminAccess() throws Exception {
        jdbcTemplate.update("update auth.account set must_change_password = false where role = 'ADMIN'");
        MvcResult login = login(ADMIN_USERNAME, ADMIN_PASSWORD);
        assertThat(login.getResponse().getStatus()).isEqualTo(200);
        return cookieValue(login, ACCESS_COOKIE);
    }

    private JsonNode createUser(String adminToken, String username, String role) throws Exception {
        MvcResult result = createUserRequest(adminToken, username, role);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return asJson(result);
    }

    private MvcResult createUserRequest(String adminToken, String username, String role) throws Exception {
        return mockMvc.perform(withAccessCookie(post("/api/users"), adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", username,
                                "displayName", "Имя " + username,
                                "role", role))))
                .andReturn();
    }

    private Account createAccount(String username, Role role) {
        return accountRepository.save(Account.create(
                username,
                username.toLowerCase(Locale.ROOT),
                passwordEncoder.encode(USER_PASSWORD),
                username,
                null,
                role,
                false,
                false));
    }

    private void assertForbidden(String token, MockHttpServletRequestBuilder builder) throws Exception {
        MvcResult result = mockMvc.perform(withAccessCookie(builder, token)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(asJson(result).get("error").asText()).isEqualTo("forbidden");
    }

    private JsonNode asJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static JsonNode find(JsonNode array, String username) {
        for (JsonNode node : array) {
            if (username.equals(node.get("username").asText())) {
                return node;
            }
        }
        throw new AssertionError("no account '" + username + "' in " + array);
    }
}
