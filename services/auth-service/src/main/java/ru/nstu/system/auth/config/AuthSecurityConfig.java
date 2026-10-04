package ru.nstu.system.auth.config;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
import ru.nstu.system.security.RoleNames;
import ru.nstu.system.security.servlet.NstuJwtAuthenticationFilter;

/**
 * Spring Security setup for {@code auth-service} (design.md D4, D6, D7, D10).
 *
 * <ul>
 *   <li>stateless sessions — authentication comes only from the access token;</li>
 *   <li>CSRF disabled: the API is cookie-based but protected by {@code SameSite=Lax}
 *       and is not reachable cross-site;</li>
 *   <li>{@link NstuJwtAuthenticationFilter} attaches a validated token, then
 *       {@link PasswordChangeRequiredFilter} enforces the restricted-token allowlist
 *       ({@code POST /api/auth/password}, {@code POST /api/auth/logout},
 *       {@code GET /api/auth/me});</li>
 *   <li>public routes: login, refresh, guest, {@code GET /api/site},
 *       actuator health and the error dispatch;</li>
 *   <li>{@link RoleHierarchyFactory#roleHierarchy()} provides
 *       {@code ADMIN > STAFF > STUDENT > GUEST} for future {@code hasRole} checks.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class AuthSecurityConfig {

    private static final String CONTENT_TYPE_JSON = "application/json";

    @Bean
    public SecurityFilterChain authSecurityFilterChain(
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
                        .requestMatchers(HttpMethod.POST,
                                "/api/auth/login", "/api/auth/refresh", "/api/auth/guest").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/site").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/error").permitAll()
                        // User administration is ADMIN-only (task 5.11). Note that the
                        // role hierarchy grants downwards only, so STAFF/STUDENT/GUEST
                        // never satisfy hasRole("ADMIN").
                        .requestMatchers("/api/users/**").hasRole(RoleNames.ADMIN)
                        // Administration endpoints, e.g. site settings (task 5.12).
                        // GET /api/site stays public (matched above).
                        .requestMatchers("/api/admin/**").hasRole(RoleNames.ADMIN)
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

    /** Shared role hierarchy (design.md D10). */
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
