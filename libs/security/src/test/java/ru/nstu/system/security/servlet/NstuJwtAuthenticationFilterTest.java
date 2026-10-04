package ru.nstu.system.security.servlet;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import ru.nstu.system.security.AccessTokenParser;
import ru.nstu.system.security.JwtProperties;
import ru.nstu.system.security.SecurityContextSupport;
import ru.nstu.system.security.TokenIssuer;

/** Unit tests for {@link NstuJwtAuthenticationFilter}. */
class NstuJwtAuthenticationFilterTest {

    private static final String SECRET = "unit-test-secret-0123456789-abcdefghijkl";

    private AccessTokenParser parser;

    private TokenIssuer issuer;

    private NstuJwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(SECRET);
        properties.setIssuer("nstu-system");
        parser = new AccessTokenParser(properties);
        issuer = new TokenIssuer(properties, Clock.systemUTC());
        filter = new NstuJwtAuthenticationFilter(parser);
    }

    @AfterEach
    void tearDown() {
        SecurityContextSupport.clear(null);
    }

    @Test
    void continuesWithoutAuthenticationWhenTokenIsAbsent() throws Exception {
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (request, response) -> chainCalled.set(true));

        assertThat(chainCalled).isTrue();
        assertThat(SecurityContextSupport.currentToken()).isEmpty();
    }

    @Test
    void attachesAuthenticationForValidToken() throws Exception {
        String token = issuer.issueAccessToken("account-1", Set.of("ADMIN"), true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("access_token", token));
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, new MockHttpServletResponse(),
                (req, response) -> chainCalled.set(true));

        assertThat(chainCalled).isTrue();
        assertThat(SecurityContextSupport.currentToken())
                .hasValueSatisfying(parsed -> {
                    assertThat(parsed.subject()).isEqualTo("account-1");
                    assertThat(parsed.roles()).containsExactly("ADMIN");
                    assertThat(parsed.passwordChangeRequired()).isTrue();
                });
        assertThat(SecurityContextSupport.tokenFromRequest(request)).isPresent();
    }

    @Test
    void continuesWithoutAuthenticationForInvalidToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("access_token", "not-a-jwt"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> chainCalled.set(true));

        // The filter must not answer itself: the authorization rules decide whether
        // the anonymous request is allowed (public route) or gets a JSON 401.
        assertThat(chainCalled).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEmpty();
        assertThat(SecurityContextSupport.currentToken()).isEmpty();
        assertThat(SecurityContextSupport.tokenFromRequest(request)).isEmpty();
    }

    @Test
    void continuesWithoutAuthenticationForExpiredToken() throws Exception {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(SECRET);
        properties.setIssuer("nstu-system");
        Clock past = Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneOffset.UTC);
        String expired = new TokenIssuer(properties, past)
                .issueAccessToken("account-1", Set.of("STUDENT"), false);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("access_token", expired));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> chainCalled.set(true));

        assertThat(chainCalled).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextSupport.currentToken()).isEmpty();
    }
}
