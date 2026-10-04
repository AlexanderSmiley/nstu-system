package ru.nstu.system.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Bridges a validated {@link ParsedToken} into the Spring Security context.
 *
 * <p>Used by the servlet services after the request-scoped authentication filter
 * has parsed the access token. The original token is stored both as the
 * authentication {@code details} and as a request attribute, so downstream
 * components (notably {@link PasswordChangeRequiredFilter}) can retrieve it
 * without re-parsing.</p>
 *
 * <p>The gateway uses {@link AccessTokenParser} directly in its reactive filter
 * chain and does not need this class.</p>
 */
public final class SecurityContextSupport {

    /** Request attribute holding the parsed token for the current request. */
    public static final String PARSED_TOKEN_ATTRIBUTE =
            SecurityContextSupport.class.getName() + ".PARSED_TOKEN";

    private SecurityContextSupport() {
    }

    /**
     * Builds an authentication with {@code ROLE_}-prefixed authorities and the
     * token itself attached as details.
     */
    public static Authentication toAuthentication(ParsedToken token) {
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        token.subject(), null, toAuthorities(token.roles()));
        authentication.setDetails(token);
        return authentication;
    }

    /**
     * Store the token in the request attribute and in the security context.
     */
    public static void attach(HttpServletRequest request, ParsedToken token) {
        if (token == null) {
            clear(request);
            return;
        }
        request.setAttribute(PARSED_TOKEN_ATTRIBUTE, token);
        SecurityContextHolder.getContext().setAuthentication(toAuthentication(token));
    }

    /**
     * @return the parsed token attached to this request, if any
     */
    public static Optional<ParsedToken> tokenFromRequest(HttpServletRequest request) {
        Object attribute = request.getAttribute(PARSED_TOKEN_ATTRIBUTE);
        if (attribute instanceof ParsedToken token) {
            return Optional.of(token);
        }
        return currentToken();
    }

    /**
     * @return the parsed token of the current security context, if any
     */
    public static Optional<ParsedToken> currentToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getDetails() instanceof ParsedToken token) {
            return Optional.of(token);
        }
        return Optional.empty();
    }

    /** Clears the security context and the request attribute. */
    public static void clear(HttpServletRequest request) {
        if (request != null) {
            request.removeAttribute(PARSED_TOKEN_ATTRIBUTE);
        }
        SecurityContextHolder.clearContext();
    }

    /** Maps bare role names to {@code ROLE_}-prefixed authorities. */
    public static Collection<GrantedAuthority> toAuthorities(Set<String> roles) {
        return RoleHierarchyFactory.toAuthorities(roles);
    }
}
