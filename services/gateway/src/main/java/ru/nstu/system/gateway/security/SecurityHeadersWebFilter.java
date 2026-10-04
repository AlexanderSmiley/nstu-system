package ru.nstu.system.gateway.security;

import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Adds the baseline security response headers required for the gateway
 * (task 4.5).
 *
 * <p>The headers are set in a {@code beforeCommit} callback, i.e. after the
 * downstream service headers have been copied into the response and immediately
 * before the bytes are flushed. That keeps the values stable even when a proxied
 * upstream sends a conflicting header, and it also covers responses written by
 * other filters (auth errors, CORS preflight). CSP is intentionally not set here:
 * it is already configured in nginx.</p>
 */
@Component
public class SecurityHeadersWebFilter implements WebFilter, Ordered {

    static final String CONTENT_TYPE_OPTIONS_HEADER = "X-Content-Type-Options";

    static final String CONTENT_TYPE_OPTIONS_VALUE = "nosniff";

    static final String REFERRER_POLICY_HEADER = "Referrer-Policy";

    static final String REFERRER_POLICY_VALUE = "no-referrer";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        exchange.getResponse().beforeCommit(() -> {
            HttpHeaders headers = exchange.getResponse().getHeaders();
            headers.set(CONTENT_TYPE_OPTIONS_HEADER, CONTENT_TYPE_OPTIONS_VALUE);
            headers.set(REFERRER_POLICY_HEADER, REFERRER_POLICY_VALUE);
            return Mono.empty();
        });
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
