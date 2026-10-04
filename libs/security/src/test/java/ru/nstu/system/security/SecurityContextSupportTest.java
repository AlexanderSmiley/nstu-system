package ru.nstu.system.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Tests for the bridge between parsed tokens and the security context.
 */
class SecurityContextSupportTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static ParsedToken token() {
        return new ParsedToken(
                "acc-7", Set.of(RoleNames.ADMIN), false,
                Instant.now().plusSeconds(900), Instant.now());
    }

    @Test
    void buildsAuthenticationWithRoleAuthorities() {
        Authentication authentication = SecurityContextSupport.toAuthentication(token());

        assertThat(authentication.getPrincipal()).isEqualTo("acc-7");
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
        assertThat(authentication.getDetails()).isInstanceOf(ParsedToken.class);
    }

    @Test
    void attachesTokenToRequestAndContext() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        ParsedToken parsedToken = token();

        SecurityContextSupport.attach(request, parsedToken);

        assertThat(SecurityContextSupport.tokenFromRequest(request)).contains(parsedToken);
        assertThat(SecurityContextSupport.currentToken()).contains(parsedToken);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    void clearRemovesTokenAndContext() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        SecurityContextSupport.attach(request, token());

        SecurityContextSupport.clear(request);

        assertThat(SecurityContextSupport.tokenFromRequest(request)).isEmpty();
        assertThat(SecurityContextSupport.currentToken()).isEmpty();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
