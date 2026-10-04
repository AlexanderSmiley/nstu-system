package ru.nstu.system.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Integration tests of the per-account preferences API (change
 * add-preferences-and-calendar-ui, design.md D2-D4): defaults, partial updates,
 * colour reset, validation, access control, lazy row creation and the
 * administrator profile.
 *
 * <p>Uses the shared PostgreSQL/RabbitMQ containers and MockMvc harness from
 * {@link AbstractStudentIntegrationTest}; the database is reset before every test,
 * which also removes preferences via the profile foreign-key cascade.</p>
 */
class ProfilePreferencesIntegrationTest extends AbstractStudentIntegrationTest {

    private static final String PREFERENCES_URL = "/api/students/me/preferences";

    // ------------------------------------------------------------------
    // Defaults and lazy row creation
    // ------------------------------------------------------------------

    @Test
    void returnsDefaultsAndCreatesTheRowLazily() throws Exception {
        UUID accountId = insertProfile(AWAY_FULL_NAME);
        assertThat(preferencesRowCount(accountId)).isZero();

        mockMvc.perform(authorized(get(PREFERENCES_URL), studentToken(accountId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modules.events").value(true))
                .andExpect(jsonPath("$.modules.calendar").value(true))
                .andExpect(jsonPath("$.modules.notes").value(true))
                .andExpect(jsonPath("$.calendarColors.ME").value("#ffffff"))
                .andExpect(jsonPath("$.calendarColors.GROUP").value("#cfe3ff"))
                .andExpect(jsonPath("$.calendarColors.STAFF").value("#d9dde3"));

        assertThat(preferencesRowCount(accountId)).isEqualTo(1);

        // A second read must not insert a duplicate row.
        mockMvc.perform(authorized(get(PREFERENCES_URL), studentToken(accountId)))
                .andExpect(status().isOk());
        assertThat(preferencesRowCount(accountId)).isEqualTo(1);
    }

    @Test
    void updatesOnlyTheGivenModuleAndKeepsEverythingElse() throws Exception {
        UUID accountId = insertProfile(AWAY_FULL_NAME);

        mockMvc.perform(authorized(patch(PREFERENCES_URL), studentToken(accountId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("modules", Map.of("calendar", false)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modules.events").value(true))
                .andExpect(jsonPath("$.modules.calendar").value(false))
                .andExpect(jsonPath("$.modules.notes").value(true))
                .andExpect(jsonPath("$.calendarColors.ME").value("#ffffff"))
                .andExpect(jsonPath("$.calendarColors.GROUP").value("#cfe3ff"))
                .andExpect(jsonPath("$.calendarColors.STAFF").value("#d9dde3"));

        // The change is persisted and survives a re-read.
        mockMvc.perform(authorized(get(PREFERENCES_URL), studentToken(accountId)))
                .andExpect(jsonPath("$.modules.calendar").value(false))
                .andExpect(jsonPath("$.modules.events").value(true))
                .andExpect(jsonPath("$.modules.notes").value(true));
    }

    // ------------------------------------------------------------------
    // Calendar colours
    // ------------------------------------------------------------------

    @Test
    void changingOneColorKeepsTheOtherTwo() throws Exception {
        UUID accountId = insertProfile(AWAY_FULL_NAME);

        mockMvc.perform(authorized(patch(PREFERENCES_URL), studentToken(accountId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("calendarColors", Map.of("GROUP", "#123456")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calendarColors.ME").value("#ffffff"))
                .andExpect(jsonPath("$.calendarColors.GROUP").value("#123456"))
                .andExpect(jsonPath("$.calendarColors.STAFF").value("#d9dde3"));
    }

    @Test
    void resettingOneColorRestoresItsDefaultAndKeepsOthers() throws Exception {
        UUID accountId = insertProfile(AWAY_FULL_NAME);
        String token = studentToken(accountId);

        mockMvc.perform(authorized(patch(PREFERENCES_URL), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("calendarColors", Map.of("ME", "#111111", "STAFF", "#222222")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calendarColors.ME").value("#111111"))
                .andExpect(jsonPath("$.calendarColors.STAFF").value("#222222"));

        // "Reset" is expressed by sending the default value back for one audience.
        mockMvc.perform(authorized(patch(PREFERENCES_URL), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("calendarColors", Map.of("STAFF", "#d9dde3")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calendarColors.ME").value("#111111"))
                .andExpect(jsonPath("$.calendarColors.GROUP").value("#cfe3ff"))
                .andExpect(jsonPath("$.calendarColors.STAFF").value("#d9dde3"));
    }

    @Test
    void rejectsInvalidColorsAndLeavesStoredValuesUnchanged() throws Exception {
        UUID accountId = insertProfile(AWAY_FULL_NAME);
        String token = studentToken(accountId);

        // Materialise the default row first.
        mockMvc.perform(authorized(get(PREFERENCES_URL), token)).andExpect(status().isOk());

        for (String invalid : new String[] {"red", "#fff", "#12345g"}) {
            mockMvc.perform(authorized(patch(PREFERENCES_URL), token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("calendarColors", Map.of("ME", invalid)))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("invalid_color"));
        }

        mockMvc.perform(authorized(get(PREFERENCES_URL), token))
                .andExpect(jsonPath("$.calendarColors.ME").value("#ffffff"))
                .andExpect(jsonPath("$.calendarColors.GROUP").value("#cfe3ff"))
                .andExpect(jsonPath("$.calendarColors.STAFF").value("#d9dde3"));
    }

    // ------------------------------------------------------------------
    // Module validation
    // ------------------------------------------------------------------

    @Test
    void rejectsUnknownModule() throws Exception {
        UUID accountId = insertProfile(AWAY_FULL_NAME);

        mockMvc.perform(authorized(patch(PREFERENCES_URL), studentToken(accountId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("modules", Map.of("admin", true)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_module"));
    }

    @Test
    void rejectsNonBooleanModuleValue() throws Exception {
        UUID accountId = insertProfile(AWAY_FULL_NAME);

        mockMvc.perform(authorized(patch(PREFERENCES_URL), studentToken(accountId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("modules", Map.of("calendar", "yes")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_module"));
    }

    // ------------------------------------------------------------------
    // Access control
    // ------------------------------------------------------------------

    @Test
    void guestIsForbidden() throws Exception {
        mockMvc.perform(authorized(get(PREFERENCES_URL), guestToken()))
                .andExpect(status().isForbidden());
        mockMvc.perform(authorized(patch(PREFERENCES_URL), guestToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("modules", Map.of("calendar", false)))))
                .andExpect(status().isForbidden());
    }

    @Test
    void requestsWithoutTokenAreUnauthorized() throws Exception {
        mockMvc.perform(get(PREFERENCES_URL)).andExpect(status().isUnauthorized());
        mockMvc.perform(patch(PREFERENCES_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("modules", Map.of("calendar", false)))))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    // Administrator profile and preferences
    // ------------------------------------------------------------------

    @Test
    void adminProfileIsCreatedLazilyAndIdempotently() throws Exception {
        UUID accountId = UUID.randomUUID();
        String token = adminToken(accountId);
        assertThat(profileExists(accountId)).isFalse();

        mockMvc.perform(authorized(get("/api/students/me"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId.toString()))
                .andExpect(jsonPath("$.fullName").value("Пользователь"));

        mockMvc.perform(authorized(get("/api/students/me"), token))
                .andExpect(status().isOk());
        assertThat(profileCount(accountId)).isEqualTo(1);
    }

    @Test
    void adminWithoutProfileGetsPreferencesAndTheProfileIsCreated() throws Exception {
        UUID accountId = UUID.randomUUID();
        assertThat(profileExists(accountId)).isFalse();

        mockMvc.perform(authorized(get(PREFERENCES_URL), adminToken(accountId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modules.events").value(true))
                .andExpect(jsonPath("$.calendarColors.ME").value("#ffffff"));

        assertThat(profileExists(accountId)).isTrue();
        assertThat(preferencesRowCount(accountId)).isEqualTo(1);
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

    private int preferencesRowCount(UUID profileId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from student.profile_preferences where profile_id = ?",
                Integer.class, profileId);
        return count == null ? 0 : count;
    }
}
