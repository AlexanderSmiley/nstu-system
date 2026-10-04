package ru.nstu.system.student.config;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
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
 * Spring Security setup for {@code student-service} (design.md D4, D7, D10, D12).
 *
 * <p>Two chains, separated by {@code securityMatcher}:</p>
 * <ol>
 *   <li><b>{@code /internal/**}</b> — no JWT. Access is granted solely by the
 *       {@link InternalTokenFilter} ({@code X-Internal-Token}); the gateway has no
 *       route for this namespace, so it is reachable only inside the private
 *       network.</li>
 *   <li><b>everything else</b> — stateless, CSRF disabled, JWT-authenticated.
 *       {@link NstuJwtAuthenticationFilter} attaches a validated token, then
 *       {@link PasswordChangeRequiredFilter} blocks restricted tokens everywhere
 *       (this service has no password-change allowlist route). Actuator health is
 *       public.</li>
 * </ol>
 *
 * <p>Whether the caller owns a profile (guest vs student/staff/admin) is decided
 * in the controller/service layer, where a precise 403/404 can be returned.</p>
 */
@Configuration
@EnableWebSecurity
public class StudentSecurityConfig {

    private static final String CONTENT_TYPE_JSON = "application/json";

    /**
     * Internal API chain: header-based protection, no JWT, everything permitted at
     * the Spring Security level (the filter has already rejected bad callers).
     */
    @Bean
    @Order(1)
    public SecurityFilterChain internalSecurityFilterChain(
            HttpSecurity http,
            NstuInternalProperties internalProperties) throws Exception {

        InternalTokenFilter internalTokenFilter = new InternalTokenFilter(internalProperties);

        http
                .securityMatcher("/internal/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .addFilterBefore(internalTokenFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** Public JSON API chain: stateless JWT authentication (design.md D6, D10). */
    @Bean
    @Order(2)
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
