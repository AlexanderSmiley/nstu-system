package ru.nstu.system.security.servlet;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.nstu.system.security.AccessTokenParser;
import ru.nstu.system.security.InvalidTokenException;
import ru.nstu.system.security.ParsedToken;
import ru.nstu.system.security.SecurityContextSupport;

/**
 * Servlet authentication filter shared by every servlet service (design.md D6, D10).
 *
 * <p>Behaviour:</p>
 * <ul>
 *   <li>No token in the request → the chain continues without authentication.
 *       Public routes are authorised by the service's Spring Security
 *       configuration.</li>
 *   <li>Token present but malformed, forged, expired or issued elsewhere → the
 *       chain continues <em>without</em> authentication. The filter never writes a
 *       response itself: protected routes are then rejected by the authorization
 *       rules through the configured {@code authenticationEntryPoint} (JSON
 *       {@code 401}), while public routes (for example {@code POST
 *       /api/auth/refresh} carrying a stale {@code access_token} cookie) still
 *       work. This is what makes transparent session refresh possible.</li>
 *   <li>Token valid → the authentication is stored in the Spring Security context
 *       and as a request attribute via
 *       {@link SecurityContextSupport#attach(HttpServletRequest, ParsedToken)} so
 *       that {@code PasswordChangeRequiredFilter} and the controllers can access it
 *       without re-parsing.</li>
 * </ul>
 *
 * <p>The filter is deliberately not a Spring bean annotated with {@code @Component}:
 * services create it explicitly and add it to their {@code SecurityFilterChain},
 * which avoids double registration as a plain servlet filter.</p>
 */
public class NstuJwtAuthenticationFilter extends OncePerRequestFilter {

    private final AccessTokenParser accessTokenParser;

    public NstuJwtAuthenticationFilter(AccessTokenParser accessTokenParser) {
        this.accessTokenParser = Objects.requireNonNull(accessTokenParser, "accessTokenParser");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        Optional<String> rawToken = BearerOrCookieTokenResolver.resolve(request);
        if (rawToken.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        ParsedToken token;
        try {
            token = accessTokenParser.parse(rawToken.get());
        } catch (InvalidTokenException ex) {
            // A stale or forged cookie must not fail the whole request: public
            // routes (login/refresh) stay reachable, protected ones are rejected
            // later by the authorization rules with a JSON 401.
            filterChain.doFilter(request, response);
            return;
        }

        SecurityContextSupport.attach(request, token);
        filterChain.doFilter(request, response);
    }
}
