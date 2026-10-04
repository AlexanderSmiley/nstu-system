package ru.nstu.system.student.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration of the service-to-service internal API (design.md D12).
 *
 * <p>Bound from {@code nstu.internal.token}: the value of the
 * {@code X-Internal-Token} header required by {@code /internal/**}. The token is
 * supplied through the environment ({@code INTERNAL_TOKEN}) and must never be
 * committed. A blank value makes {@link InternalTokenFilter} reject every
 * internal call (fail closed).</p>
 */
@Component
@ConfigurationProperties(prefix = "nstu.internal")
public class NstuInternalProperties {

    private String token;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
