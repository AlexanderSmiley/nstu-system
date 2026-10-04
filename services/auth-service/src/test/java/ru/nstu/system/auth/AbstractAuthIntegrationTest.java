package ru.nstu.system.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import ru.nstu.system.auth.domain.Account;
import ru.nstu.system.auth.domain.AccountRepository;
import ru.nstu.system.auth.domain.RefreshTokenRepository;
import ru.nstu.system.auth.domain.Role;
import ru.nstu.system.auth.service.RefreshTokenGenerator;
import ru.nstu.system.security.AccessTokenParser;

/**
 * Shared setup for the authentication integration tests (tasks 5.1-5.7).
 *
 * <p>One Spring context and one PostgreSQL container are reused by all subclasses.
 * Tests are intentionally <em>not</em> wrapped in a transaction: reuse detection
 * revokes tokens in a separate {@code REQUIRES_NEW} transaction, which must observe
 * committed state. State is reset before every test instead, restoring the
 * bootstrapped administrator and removing test data.</p>
 */
@SpringBootTest(properties = {
        "admin.username=admin",
        "admin.password=Admin-Pass1",
        "nstu.jwt.secret=nstu-integration-test-secret-0123456789",
        "nstu.auth.cookie-secure=false",
        // Keep the outbox scheduler away from RabbitMQ: tests assert on the committed
        // outbox rows, not on publication (no broker runs in this test JVM).
        "nstu.outbox.poll-interval=PT24H"
})
@AutoConfigureMockMvc
abstract class AbstractAuthIntegrationTest {

    protected static final String ADMIN_USERNAME = "admin";
    protected static final String ADMIN_PASSWORD = "Admin-Pass1";

    /** Password of the active accounts created by {@link #newAccount(String, Role)}. */
    protected static final String FIXTURE_PASSWORD = "Staff-Pass1";

    protected static final String ACCESS_COOKIE = "access_token";
    protected static final String REFRESH_COOKIE = "refresh_token";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected AccountRepository accountRepository;

    @Autowired
    protected RefreshTokenRepository refreshTokenRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected RefreshTokenGenerator refreshTokenGenerator;

    @Autowired
    protected AccessTokenParser accessTokenParser;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PostgresTestContainer.INSTANCE::getJdbcUrl);
        registry.add("spring.datasource.username", PostgresTestContainer.INSTANCE::getUsername);
        registry.add("spring.datasource.password", PostgresTestContainer.INSTANCE::getPassword);
    }

    @BeforeEach
    void resetDatabase() {
        jdbcTemplate.update("delete from auth.refresh_token");
        jdbcTemplate.update("delete from auth.account where role <> 'ADMIN'");
        jdbcTemplate.update(
                "update auth.account set password_hash = ?, must_change_password = true, "
                        + "blocked = false, display_name = username where role = 'ADMIN'",
                passwordEncoder.encode(ADMIN_PASSWORD));
    }

    /** Performs {@code POST /api/auth/login} and returns the raw result. */
    protected MvcResult login(String username, String password) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", password))))
                .andReturn();
    }

    /** Performs {@code POST /api/auth/refresh} with the given refresh cookie. */
    protected MvcResult refresh(String rawRefreshToken) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/refresh")
                        .cookie(new Cookie(REFRESH_COOKIE, rawRefreshToken)))
                .andReturn();
    }

    protected MockHttpServletRequestBuilder withAccessCookie(MockHttpServletRequestBuilder builder, String token) {
        return builder.cookie(new Cookie(ACCESS_COOKIE, token));
    }

    protected String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    protected UUID adminId() {
        return accountRepository.findByUsernameNormalized(ADMIN_USERNAME)
                .orElseThrow(() -> new AssertionError("administrator account is missing"))
                .getId();
    }

    /** @return the full {@code Set-Cookie} header for the given cookie name, or {@code null} */
    protected static String setCookieHeader(MvcResult result, String name) {
        for (String header : result.getResponse().getHeaders("Set-Cookie")) {
            if (header.startsWith(name + "=")) {
                return header;
            }
        }
        return null;
    }

    /** @return the value of the given cookie from the response, or {@code null} */
    protected static String cookieValue(MvcResult result, String name) {
        String header = setCookieHeader(result, name);
        if (header == null) {
            return null;
        }
        int separator = header.indexOf(';');
        String nameValue = separator < 0 ? header : header.substring(0, separator);
        return nameValue.substring(name.length() + 1);
    }

    /**
     * Clears the bootstrap administrator's mandatory-change flag and logs in,
     * returning a full (unrestricted) access token.
     */
    protected String adminAccess() throws Exception {
        jdbcTemplate.update("update auth.account set must_change_password = false where role = 'ADMIN'");
        MvcResult login = login(ADMIN_USERNAME, ADMIN_PASSWORD);
        assertThat(login.getResponse().getStatus()).isEqualTo(200);
        return cookieValue(login, ACCESS_COOKIE);
    }

    /**
     * Persists an active account with a known password and no forced password
     * change, for tests that need a non-administrator identity.
     */
    protected Account newAccount(String username, Role role) {
        return accountRepository.save(Account.create(
                username,
                username.toLowerCase(Locale.ROOT),
                passwordEncoder.encode(FIXTURE_PASSWORD),
                username,
                null,
                role,
                false,
                false));
    }

    /** @return the parsed {@code payload} (the event envelope) of the first outbox row of the type */
    protected JsonNode outboxEvent(String eventType) throws Exception {
        String payload = jdbcTemplate.queryForObject(
                "select payload::text from auth.outbox where event_type = ?",
                String.class,
                eventType);
        assertThat(payload).as("outbox event %s", eventType).isNotNull();
        return objectMapper.readTree(payload);
    }

    /** @return how many outbox rows of the given event type exist */
    protected int outboxCount(String eventType) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from auth.outbox where event_type = ?",
                Integer.class,
                eventType);
        return count == null ? 0 : count;
    }
}
