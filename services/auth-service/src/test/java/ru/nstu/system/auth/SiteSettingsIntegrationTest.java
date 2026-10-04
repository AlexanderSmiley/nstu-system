package ru.nstu.system.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import ru.nstu.system.auth.domain.Account;
import ru.nstu.system.auth.domain.Role;

/**
 * Integration tests for the site name setting (identity spec "Название сайта",
 * design.md D27, task 5.12).
 *
 * <p>{@code GET /api/site} must work without any token and expose exactly the
 * {@code name} field; {@code PUT /api/admin/site} is ADMIN-only, validated and
 * persists the trimmed value together with {@code updated_by}.</p>
 */
class SiteSettingsIntegrationTest extends AbstractAuthIntegrationTest {

    private static final String SEEDED_NAME = "NSTU System";
    private static final String SITE_NAME_KEY = "site.name";

    @BeforeEach
    void resetSiteSetting() {
        jdbcTemplate.update("delete from auth.outbox");
        jdbcTemplate.update(
                "update auth.site_setting set value = to_jsonb(cast(? as text)), updated_by = null"
                        + " where key = ?",
                SEEDED_NAME,
                SITE_NAME_KEY);
    }

    @Test
    void siteNameIsPublicAndExposesOnlyName() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/site")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = asJson(result);
        assertThat(body.size()).isEqualTo(1);
        assertThat(body.get("name").asText()).isEqualTo(SEEDED_NAME);
    }

    @Test
    void invalidNamesAreRejectedAndValueIsUnchanged() throws Exception {
        String admin = adminAccess();

        for (String invalid : List.of("", "   ", "a".repeat(121))) {
            MvcResult result = putSite(admin, invalid);
            assertThat(result.getResponse().getStatus()).as("name='%s'", invalid).isEqualTo(400);
            assertThat(asJson(result).get("error").asText()).isEqualTo("invalid_site_name");
        }

        MvcResult missingName = mockMvc.perform(withAccessCookie(put("/api/admin/site"), admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn();
        assertThat(missingName.getResponse().getStatus()).isEqualTo(400);

        assertThat(storedName()).isEqualTo(SEEDED_NAME);
    }

    @Test
    void nonAdministratorsCannotChangeName() throws Exception {
        Account staff = newAccount("site-staff", Role.STAFF);
        newAccount("site-student", Role.STUDENT);
        String staffToken = cookieValue(login("site-staff", FIXTURE_PASSWORD), ACCESS_COOKIE);
        String studentToken = cookieValue(login("site-student", FIXTURE_PASSWORD), ACCESS_COOKIE);
        String guestToken = cookieValue(mockMvc.perform(post("/api/auth/guest")).andReturn(), ACCESS_COOKIE);

        for (String token : List.of(staffToken, studentToken, guestToken)) {
            MvcResult result = mockMvc.perform(withAccessCookie(put("/api/admin/site"), token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("name", "Hacked"))))
                    .andReturn();
            assertThat(result.getResponse().getStatus()).isEqualTo(403);
        }
        assertThat(storedName()).isEqualTo(SEEDED_NAME);
    }

    @Test
    void administratorCanChangeNameAndItIsVisiblePublicly() throws Exception {
        String admin = adminAccess();

        MvcResult put = putSite(admin, "  Новое Название  ");
        assertThat(put.getResponse().getStatus()).isEqualTo(200);
        assertThat(asJson(put).get("name").asText()).isEqualTo("Новое Название");

        MvcResult get = mockMvc.perform(get("/api/site")).andReturn();
        assertThat(get.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = asJson(get);
        assertThat(body.size()).isEqualTo(1);
        assertThat(body.get("name").asText()).isEqualTo("Новое Название");

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select value #>> '{}' as name, updated_by from auth.site_setting where key = ?",
                SITE_NAME_KEY);
        assertThat(row.get("name")).isEqualTo("Новое Название");
        assertThat(String.valueOf(row.get("updated_by"))).isEqualTo(adminId().toString());
    }

    // --- helpers ---------------------------------------------------------------

    private MvcResult putSite(String adminToken, String name) throws Exception {
        return mockMvc.perform(withAccessCookie(put("/api/admin/site"), adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name))))
                .andReturn();
    }

    private String storedName() {
        return jdbcTemplate.queryForObject(
                "select value #>> '{}' from auth.site_setting where key = ?",
                String.class,
                SITE_NAME_KEY);
    }

    private JsonNode asJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
