package ru.nstu.system.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import ru.nstu.system.auth.domain.Role;

/**
 * Integration tests for the site icon API (change add-site-icon, design.md
 * D1-D3): content detection, ADMIN-only writes, public reads with caching
 * headers, conditional GET and the idempotent reset.
 */
class SiteIconIntegrationTest extends AbstractAuthIntegrationTest {

    private static final int MAX_BYTES = 256 * 1024;

    private static final byte[] PNG = new byte[] {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52, 0x01, 0x02, 0x03
    };

    private static final byte[] ICO = new byte[] {0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x10};

    private static final byte[] SVG = ("<svg xmlns=\"http://www.w3.org/2000/svg\" "
            + "viewBox=\"0 0 16 16\"><rect width=\"16\" height=\"16\" fill=\"red\"/></svg>")
            .getBytes(StandardCharsets.UTF_8);

    private static final byte[] PDF = "%PDF-1.7\n%% fake pdf\n".getBytes(StandardCharsets.UTF_8);

    private static final String ICON_PATH = "/api/site/icon";

    private static final String ADMIN_ICON_PATH = "/api/admin/site/icon";

    @BeforeEach
    void resetSiteAsset() {
        jdbcTemplate.update("delete from auth.site_asset");
        jdbcTemplate.update("delete from auth.outbox");
    }

    @Test
    void adminUploadsPngAndItIsServedPublicly() throws Exception {
        String admin = adminAccess();

        MvcResult upload = upload(admin, PNG, "icon.png", "image/png");
        assertThat(upload.getResponse().getStatus()).isEqualTo(200);
        JsonNode uploadBody = asJson(upload);
        assertThat(uploadBody.get("contentType").asText()).isEqualTo("image/png");
        assertThat(uploadBody.get("sizeBytes").asInt()).isEqualTo(PNG.length);

        MvcResult get = mockMvc.perform(get(ICON_PATH)).andReturn();
        assertThat(get.getResponse().getStatus()).isEqualTo(200);
        assertThat(get.getResponse().getContentType()).isEqualTo("image/png");
        assertThat(get.getResponse().getContentAsByteArray()).isEqualTo(PNG);
        assertThat(get.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(get.getResponse().getHeader(HttpHeaders.ETAG)).isNotBlank();
        assertThat(get.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-cache");
    }

    @Test
    void adminUploadsIcoAndContentTypeIsPreserved() throws Exception {
        String admin = adminAccess();

        MvcResult upload = upload(admin, ICO, "icon.ico", "image/x-icon");
        assertThat(upload.getResponse().getStatus()).isEqualTo(200);
        assertThat(asJson(upload).get("contentType").asText()).isEqualTo("image/x-icon");

        MvcResult get = mockMvc.perform(get(ICON_PATH)).andReturn();
        assertThat(get.getResponse().getStatus()).isEqualTo(200);
        assertThat(get.getResponse().getContentType()).isEqualTo("image/x-icon");
        assertThat(get.getResponse().getContentAsByteArray()).isEqualTo(ICO);
    }

    @Test
    void adminUploadsSvgAndItCarriesAContentSecurityPolicy() throws Exception {
        String admin = adminAccess();

        MvcResult upload = upload(admin, SVG, "icon.svg", "image/svg+xml");
        assertThat(upload.getResponse().getStatus()).isEqualTo(200);
        assertThat(asJson(upload).get("contentType").asText()).isEqualTo("image/svg+xml");

        MvcResult get = mockMvc.perform(get(ICON_PATH)).andReturn();
        assertThat(get.getResponse().getStatus()).isEqualTo(200);
        assertThat(get.getResponse().getContentType()).isEqualTo("image/svg+xml");
        assertThat(get.getResponse().getHeader("Content-Security-Policy")).isEqualTo("default-src 'none'");
    }

    @Test
    void rejectsInvalidContentAndKeepsThePreviousIcon() throws Exception {
        String admin = adminAccess();
        assertThat(upload(admin, PNG, "icon.png", "image/png").getResponse().getStatus()).isEqualTo(200);

        MvcResult rejected = upload(admin, PDF, "icon.png", "image/png");
        assertThat(rejected.getResponse().getStatus()).isEqualTo(400);
        assertThat(asJson(rejected).get("error").asText()).isEqualTo("invalid_icon");

        MvcResult get = mockMvc.perform(get(ICON_PATH)).andReturn();
        assertThat(get.getResponse().getContentAsByteArray()).isEqualTo(PNG);
    }

    @Test
    void rejectsMaliciousSvgWithScript() throws Exception {
        String admin = adminAccess();
        byte[] malicious = ("<svg xmlns=\"http://www.w3.org/2000/svg\">"
                + "<script>alert(1)</script></svg>").getBytes(StandardCharsets.UTF_8);

        MvcResult rejected = upload(admin, malicious, "icon.svg", "image/svg+xml");
        assertThat(rejected.getResponse().getStatus()).isEqualTo(400);
        assertThat(asJson(rejected).get("error").asText()).isEqualTo("invalid_icon");

        assertThat(mockMvc.perform(get(ICON_PATH)).andReturn().getResponse().getStatus())
                .isEqualTo(404);
    }

    @Test
    void rejectsIconLargerThanTheLimit() throws Exception {
        String admin = adminAccess();
        byte[] oversize = new byte[MAX_BYTES + 1];

        MvcResult rejected = upload(admin, oversize, "icon.png", "image/png");
        assertThat(rejected.getResponse().getStatus()).isEqualTo(413);
        assertThat(asJson(rejected).get("error").asText()).isEqualTo("icon_too_large");

        assertThat(mockMvc.perform(get(ICON_PATH)).andReturn().getResponse().getStatus())
                .isEqualTo(404);
    }

    @Test
    void nonAdministratorsCannotUploadOrReset() throws Exception {
        newAccount("icon-staff", Role.STAFF);
        newAccount("icon-student", Role.STUDENT);
        String staff = cookieValue(login("icon-staff", FIXTURE_PASSWORD), ACCESS_COOKIE);
        String student = cookieValue(login("icon-student", FIXTURE_PASSWORD), ACCESS_COOKIE);
        String guest = cookieValue(mockMvc.perform(post("/api/auth/guest")).andReturn(), ACCESS_COOKIE);

        for (String token : List.of(staff, student, guest)) {
            assertThat(upload(token, PNG, "icon.png", "image/png").getResponse().getStatus())
                    .as("upload")
                    .isEqualTo(403);
            assertThat(mockMvc.perform(withAccessCookie(delete(ADMIN_ICON_PATH), token)).andReturn()
                    .getResponse().getStatus()).as("reset").isEqualTo(403);
        }

        assertThat(upload(null, PNG, "icon.png", "image/png").getResponse().getStatus())
                .as("anonymous upload")
                .isEqualTo(401);
        assertThat(mockMvc.perform(delete(ADMIN_ICON_PATH)).andReturn().getResponse().getStatus())
                .as("anonymous reset")
                .isEqualTo(401);
    }

    @Test
    void publicGetReturnsNotFoundWhenNoIconIsStored() throws Exception {
        MvcResult result = mockMvc.perform(get(ICON_PATH)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(result.getResponse().getContentAsByteArray()).isEmpty();
    }

    @Test
    void conditionalGetReturnsNotModifiedForTheSameEtag() throws Exception {
        String admin = adminAccess();
        upload(admin, PNG, "icon.png", "image/png");

        MvcResult first = mockMvc.perform(get(ICON_PATH)).andReturn();
        String etag = first.getResponse().getHeader(HttpHeaders.ETAG);
        assertThat(etag).isNotBlank();

        MvcResult conditional = mockMvc.perform(get(ICON_PATH)
                        .header(HttpHeaders.IF_NONE_MATCH, etag))
                .andReturn();
        assertThat(conditional.getResponse().getStatus()).isEqualTo(304);
        assertThat(conditional.getResponse().getContentAsByteArray()).isEmpty();
    }

    @Test
    void resetIsIdempotent() throws Exception {
        String admin = adminAccess();
        upload(admin, PNG, "icon.png", "image/png");

        assertThat(mockMvc.perform(withAccessCookie(delete(ADMIN_ICON_PATH), admin)).andReturn()
                .getResponse().getStatus()).isEqualTo(204);
        assertThat(mockMvc.perform(get(ICON_PATH)).andReturn().getResponse().getStatus())
                .isEqualTo(404);

        assertThat(mockMvc.perform(withAccessCookie(delete(ADMIN_ICON_PATH), admin)).andReturn()
                .getResponse().getStatus()).isEqualTo(204);
        assertThat(mockMvc.perform(get(ICON_PATH)).andReturn().getResponse().getStatus())
                .isEqualTo(404);
    }

    @Test
    void restrictedTokenCanReadThePublicIcon() throws Exception {
        // The password-change screen must be able to show the icon: issue a
        // restricted token before clearing the mandatory-change flag.
        String restricted = cookieValue(login(ADMIN_USERNAME, ADMIN_PASSWORD), ACCESS_COOKIE);
        String admin = adminAccess();
        upload(admin, PNG, "icon.png", "image/png");

        MvcResult get = mockMvc.perform(withAccessCookie(get(ICON_PATH), restricted)).andReturn();
        assertThat(get.getResponse().getStatus()).isEqualTo(200);
        assertThat(get.getResponse().getContentAsByteArray()).isEqualTo(PNG);
    }

    // --- helpers ---------------------------------------------------------------

    private MvcResult upload(String token, byte[] content, String filename, String contentType)
            throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename, contentType, content);
        var request = multipart(HttpMethod.PUT, ADMIN_ICON_PATH).file(file);
        if (token != null) {
            // Builder methods mutate in place; the cookie is attached to `request`.
            request.cookie(new Cookie(ACCESS_COOKIE, token));
        }
        return mockMvc.perform(request).andReturn();
    }

    private JsonNode asJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}