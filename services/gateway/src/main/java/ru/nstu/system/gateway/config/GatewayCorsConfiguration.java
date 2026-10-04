package ru.nstu.system.gateway.config;

import java.time.Duration;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * Programmatic reactive CORS for the gateway (design.md D4, task 4.3).
 *
 * <p>A {@link CorsWebFilter} is used instead of the gateway's {@code globalcors}
 * properties because the allowed origins must be overridable with a single
 * comma-separated environment variable ({@code NSTU_CORS_ALLOWED_ORIGINS}). The
 * filter runs before the route handler mapping, so CORS preflight
 * ({@code OPTIONS}) is answered without an access token and never reaches the
 * authorization filter or a downstream service.</p>
 *
 * <p>{@code allowCredentials=true} requires explicit origins — a wildcard is
 * rejected by Spring, which is exactly the desired behaviour for cookie-based
 * sessions.</p>
 */
@Configuration(proxyBeanMethods = false)
public class GatewayCorsConfiguration {

    private static final List<String> ALLOWED_METHODS =
            List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD");

    private static final List<String> EXPOSED_HEADERS = List.of("ETag", "Location");

    private static final Duration MAX_AGE = Duration.ofHours(1);

    @Bean
    public CorsWebFilter corsWebFilter(NstuCorsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.getAllowedOrigins());
        configuration.setAllowedMethods(ALLOWED_METHODS);
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(EXPOSED_HEADERS);
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(MAX_AGE);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return new CorsWebFilter(source);
    }
}
