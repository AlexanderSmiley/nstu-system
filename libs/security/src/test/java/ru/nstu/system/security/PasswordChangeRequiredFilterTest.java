package ru.nstu.system.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Tests for the servlet filter that blocks a restricted token outside the
 * allowlist.
 */
class PasswordChangeRequiredFilterTest {

    private final PasswordChangeRequiredFilter filter = new PasswordChangeRequiredFilter();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static ParsedToken restrictedToken() {
        return new ParsedToken(
                "acc-1", Set.of(RoleNames.STUDENT), true,
                Instant.now().plusSeconds(900), Instant.now());
    }

    private static ParsedToken normalToken() {
        return new ParsedToken(
                "acc-1", Set.of(RoleNames.STUDENT), false,
                Instant.now().plusSeconds(900), Instant.now());
    }

    private MockHttpServletResponse run(
            String method, String uri, String contextPath, ParsedToken token)
            throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        if (contextPath != null) {
            request.setContextPath(contextPath);
        }
        if (token != null) {
            SecurityContextSupport.attach(request, token);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void blocksRestrictedTokenOutsideAllowlist() throws Exception {
        MockHttpServletResponse response = run("GET", "/api/events", "", restrictedToken());
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void blocksRestrictedTokenEvenForPostEvents() throws Exception {
        MockHttpServletResponse response = run("POST", "/api/events", "", restrictedToken());
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void allowsRestrictedTokenOnPasswordChange() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/password");
        request.setRequestURI("/api/auth/password");
        SecurityContextSupport.attach(request, restrictedToken());
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void allowsRestrictedTokenOnMe() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        request.setRequestURI("/api/auth/me");
        SecurityContextSupport.attach(request, restrictedToken());
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void passesNormalTokenAnywhere() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/events");
        request.setRequestURI("/api/events");
        SecurityContextSupport.attach(request, normalToken());
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void passesAnonymousRequest() throws Exception {
        MockHttpServletResponse response = run("GET", "/api/events", "", null);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void stripsContextPathBeforeMatching() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/api/auth/password");
        request.setContextPath("/auth");
        request.setRequestURI("/auth/api/auth/password");
        SecurityContextSupport.attach(request, restrictedToken());
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
    }
}
