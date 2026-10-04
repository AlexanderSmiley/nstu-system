package ru.nstu.system.gateway.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * CORS configuration of the gateway (design.md D4, task 4.3).
 *
 * <p>Bound from {@code nstu.cors}. The allowed origins are supplied through
 * {@code NSTU_CORS_ALLOWED_ORIGINS} as a comma-separated list; the default is the
 * Vite dev server, which is the only cross-origin caller needed during
 * development.</p>
 */
@Component
@ConfigurationProperties(prefix = "nstu.cors")
public class NstuCorsProperties {

    /** Local Vite development server, used when the property is not set. */
    public static final String DEFAULT_ALLOWED_ORIGIN = "http://localhost:5173";

    private List<String> allowedOrigins = List.of(DEFAULT_ALLOWED_ORIGIN);

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins == null || allowedOrigins.isEmpty()
                ? List.of(DEFAULT_ALLOWED_ORIGIN)
                : List.copyOf(allowedOrigins);
    }
}
