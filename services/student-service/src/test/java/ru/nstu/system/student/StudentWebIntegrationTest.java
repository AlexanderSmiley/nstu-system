package ru.nstu.system.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import ru.nstu.system.contracts.Groups;
import ru.nstu.system.student.config.InternalTokenFilter;
import ru.nstu.system.student.service.StudentProfileService;

/**
 * Web-layer tests: the self-service profile API and the internal name-snapshot
 * endpoint (tasks 6.1, 6.3, 6.4).
 */
class StudentWebIntegrationTest extends AbstractStudentIntegrationTest {

    private static final String PROFILE_UPDATED = "profile.updated";

    private static final String DEFAULT_GROUP_NAME = "НГТУ — группа по умолчанию";

    @Autowired
    private StudentProfileService profileService;

    // ------------------------------------------------------------------
    // GET /api/students/me
    // ------------------------------------------------------------------

    @Test
    void getMeReturnsProfile() throws Exception {
        UUID accountId = insertProfile(AWAY_FULL_NAME);

        mockMvc.perform(authorized(get("/api/students/me"), studentToken(accountId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId.toString()))
                .andExpect(jsonPath("$.fullName").value(AWAY_FULL_NAME))
                .andExpect(jsonPath("$.groupId").value(Groups.DEFAULT_GROUP_ID.toString()))
                .andExpect(jsonPath("$.groupName").value(DEFAULT_GROUP_NAME));
    }

    @Test
    void getMeReturnsGroupNameFromDirectoryAndKeepsExistingFields() throws Exception {
        // The name must come from the directory row, not be hard-coded in Java.
        jdbcTemplate.update(
                "update student.app_group set name = ? where id = ?",
                "Тестовая учебная группа", Groups.DEFAULT_GROUP_ID);
        UUID accountId = insertProfile(AWAY_FULL_NAME);

        mockMvc.perform(authorized(get("/api/students/me"), studentToken(accountId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId.toString()))
                .andExpect(jsonPath("$.fullName").value(AWAY_FULL_NAME))
                .andExpect(jsonPath("$.groupId").value(Groups.DEFAULT_GROUP_ID.toString()))
                .andExpect(jsonPath("$.groupName").value("Тестовая учебная группа"));
    }

    @Test
    void getMeWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/students/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMeForGuestIsForbidden() throws Exception {
        mockMvc.perform(authorized(get("/api/students/me"), guestToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMeForAdminWithoutProfileIsNotFound() throws Exception {
        // An administrator authenticates successfully but owns no profile.
        mockMvc.perform(authorized(get("/api/students/me"), adminToken(UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------
    // PATCH /api/students/me
    // ------------------------------------------------------------------

    @Test
    void patchMeUpdatesFullNameAndContactsAndWritesProfileUpdatedOutbox() throws Exception {
        UUID accountId = insertProfile(AWAY_FULL_NAME);

        mockMvc.perform(authorized(patch("/api/students/me"), studentToken(accountId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "fullName", "Петров Пётр Петрович",
                                "contacts", Map.of("phone", "+7-999-000-00-00")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Петров Пётр Петрович"))
                .andExpect(jsonPath("$.contacts.phone").value("+7-999-000-00-00"))
                .andExpect(jsonPath("$.groupId").value(Groups.DEFAULT_GROUP_ID.toString()))
                .andExpect(jsonPath("$.groupName").value(DEFAULT_GROUP_NAME));

        assertThat(profileFullName(accountId)).isEqualTo("Петров Пётр Петрович");

        String payload = outboxPayload(PROFILE_UPDATED);
        assertThat(payload)
                .contains(PROFILE_UPDATED)
                .contains("Петров Пётр Петрович");
        // Task 6.4: the event is a pure name update and carries no queue state.
        assertThat(payload)
                .doesNotContain("WAITING")
                .doesNotContain("PAUSED")
                .doesNotContain("PASSED")
                .doesNotContain("\"position\"");
    }

    @Test
    void patchMeCannotChangeGroupId() throws Exception {
        UUID accountId = insertProfile(AWAY_FULL_NAME);

        mockMvc.perform(authorized(patch("/api/students/me"), studentToken(accountId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "fullName", "Сидоров Сидор",
                                "groupId", UUID.randomUUID().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupId").value(Groups.DEFAULT_GROUP_ID.toString()));

        UUID storedGroup = jdbcTemplate.queryForObject(
                "select group_id from student.student_profile where id = ?", UUID.class, accountId);
        assertThat(storedGroup).isEqualTo(Groups.DEFAULT_GROUP_ID);
    }

    @Test
    void patchMeContactsOnlyDoesNotWriteProfileUpdated() throws Exception {
        UUID accountId = insertProfile(AWAY_FULL_NAME);

        mockMvc.perform(authorized(patch("/api/students/me"), studentToken(accountId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("contacts", Map.of("telegram", "@ivanov")))))
                .andExpect(status().isOk());

        assertThat(outboxCount()).isZero();
    }

    @Test
    void patchMeWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(patch("/api/students/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "Кто-то"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void patchMeForGuestIsForbidden() throws Exception {
        mockMvc.perform(authorized(patch("/api/students/me"), guestToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "Гость"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void rollbackRevertsProfileAndOutboxTogether() {
        UUID accountId = insertProfile(AWAY_FULL_NAME);
        TransactionTemplate template = new TransactionTemplate(transactionManager);

        template.executeWithoutResult(status -> {
            profileService.updateOwnProfile(accountId, "Откат Откатович", Map.of("phone", "+7"));
            status.setRollbackOnly();
        });

        // Both writes must have been part of one transaction: rolling it back must
        // undo the profile change and remove the outbox row.
        assertThat(profileFullName(accountId)).isEqualTo(AWAY_FULL_NAME);
        assertThat(outboxCount()).isZero();
    }

    // ------------------------------------------------------------------
    // GET /internal/students/{accountId}
    // ------------------------------------------------------------------

    @Test
    void internalEndpointWithoutTokenIsForbidden() throws Exception {
        UUID accountId = insertProfile(AWAY_FULL_NAME);

        mockMvc.perform(get("/internal/students/{id}", accountId))
                .andExpect(status().isForbidden());
    }

    @Test
    void internalEndpointWithWrongTokenIsForbidden() throws Exception {
        UUID accountId = insertProfile(AWAY_FULL_NAME);

        mockMvc.perform(get("/internal/students/{id}", accountId)
                        .header(InternalTokenFilter.INTERNAL_TOKEN_HEADER, "not-the-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void internalEndpointWithValidTokenReturnsNameAndGroup() throws Exception {
        UUID accountId = insertProfile(AWAY_FULL_NAME);

        mockMvc.perform(get("/internal/students/{id}", accountId)
                        .header(InternalTokenFilter.INTERNAL_TOKEN_HEADER, INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId.toString()))
                .andExpect(jsonPath("$.fullName").value(AWAY_FULL_NAME))
                .andExpect(jsonPath("$.groupId").value(Groups.DEFAULT_GROUP_ID.toString()));
    }

    @Test
    void internalEndpointForUnknownAccountIsNotFound() throws Exception {
        mockMvc.perform(get("/internal/students/{id}", UUID.randomUUID())
                        .header(InternalTokenFilter.INTERNAL_TOKEN_HEADER, INTERNAL_TOKEN))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder builder, String token) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private String outboxPayload(String eventType) {
        return jdbcTemplate.queryForObject(
                "select payload::text from student.outbox where event_type = ?", String.class, eventType);
    }
}
