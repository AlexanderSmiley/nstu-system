package ru.nstu.system.event.config;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import ru.nstu.system.security.AccessTokenParser;
import ru.nstu.system.security.PasswordChangeRequiredFilter;
import ru.nstu.system.security.RoleHierarchyFactory;
import ru.nstu.system.security.servlet.NstuJwtAuthenticationFilter;

/**
 * Spring Security setup for {@code event-service} (design.md D4, D7, D10).
 *
 * <p>A single stateless chain:</p>
 * <ol>
 *   <li>{@link NstuJwtAuthenticationFilter} validates the bearer/cookie token and
 *       attaches it to the security context; a syntactically invalid token is
 *       answered with 401 immediately.</li>
 *   <li>{@link PasswordChangeRequiredFilter} blocks restricted tokens everywhere
 *       (this service has no password-change allowlist route).</li>
 *   <li>{@code /actuator/health} is public; every other route — including
 *       {@code /api/events/**} — requires a valid token. Guest sessions pass
 *       authentication but remain limited to {@code GUEST+} events by the service
 *       layer, and an anonymous call to the short link gets 401 (task 7.4).</li>
 * </ol>
 */
@Configuration
@EnableWebSecurity
public class EventSecurityConfig {

    private static final String CONTENT_TYPE_JSON = "application/json";

    @Bean
    public SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http,
            AccessTokenParser accessTokenParser) throws Exception {

        NstuJwtAuthenticationFilter jwtAuthenticationFilter =
                new NstuJwtAuthenticationFilter(accessTokenParser);
        PasswordChangeRequiredFilter passwordChangeRequiredFilter = new PasswordChangeRequiredFilter();

        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/error").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "unauthorized"))
                        .accessDeniedHandler((request, response, exception) ->
                                writeError(response, HttpServletResponse.SC_FORBIDDEN, "forbidden")))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(passwordChangeRequiredFilter, NstuJwtAuthenticationFilter.class);

        return http.build();
    }

    /** Shared role hierarchy {@code ADMIN > STAFF > STUDENT > GUEST} (design.md D10). */
    @Bean
    public RoleHierarchy roleHierarchy() {
        return RoleHierarchyFactory.roleHierarchy();
    }

    private static void writeError(HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status);
        response.setContentType(CONTENT_TYPE_JSON);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"error\":\"" + code + "\"}");
    }
}
