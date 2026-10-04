package ru.nstu.system.event.service;

import java.time.Duration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * HTTP client used for the one synchronous service-to-service call of group 8
 * (design.md D12): {@code GET {nstu.student-service.url}/internal/students/{id}}.
 *
 * <p>A dedicated {@link RestTemplate} keeps the internal timeouts explicit and
 * bounded ({@code connect 2s / read 3s}); the rest of the service does not make
 * outbound HTTP calls. The endpoint is protected by {@code X-Internal-Token} and
 * is not routed through the gateway.</p>
 */
@Configuration
public class StudentServiceClientConfig {

    @Bean
    public RestTemplate studentServiceRestTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(2))
                .readTimeout(Duration.ofSeconds(3))
                .build();
    }
}
