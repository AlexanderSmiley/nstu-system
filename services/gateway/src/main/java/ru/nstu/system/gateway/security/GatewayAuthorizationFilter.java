package ru.nstu.system.gateway.security;

import java.nio.charset.StandardCharsets;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import ru.nstu.system.security.AccessTokenParser;
import ru.nstu.system.security.InvalidTokenException;
import ru.nstu.system.security.ParsedToken;
import ru.nstu.system.security.PasswordChangePolicy;

/**
 * Reactive access-token gate in front of every routed request (design.md D4, D7).
 *
 * <p>Responsibilities, in order:</p>
 * <ol>
 *   <li>{@code /internal/**} is answered with {@code 404} before any routing, so
 *       internal endpoints are invisible from the outside (D12, task 4.4).</li>
 *   <li>{@code OPTIONS} (CORS preflight) and the four public routes bypass
 *       authentication.</li>
 *   <li>Every other request must carry a valid access token — either in the
 *       {@code access_token} cookie or as {@code Authorization: Bearer ...}.
 *       Missing, malformed, forged or expired tokens yield {@code 401}.</li>
 *   <li>A restricted token ({@code pwd_change_required=true}) may only reach the
 *       routes allowlisted by {@link PasswordChangePolicy}; anything else yields
 *       {@code 403}.</li>
 * </ol>
 *
 * <p>The original {@code Cookie} and {@code Authorization} headers are forwarded
 * untouched: the downstream services validate the token themselves (D4). Error
 * bodies are intentionally terse JSON codes without any detail about the
 * failure.</p>
 */
@Component
public class GatewayAuthorizationFilter implements GlobalFilter, Ordered {

    /** Name of the httpOnly cookie that carries the access token (D6). */
    public static final String ACCESS_TOKEN_COOKIE = "access_token";

    private static final String BEARER_PREFIX = "Bearer ";

    private final AccessTokenParser accessTokenParser;

    public GatewayAuthorizationFilter(AccessTokenParser accessTokenParser) {
        this.accessTokenParser = accessTokenParser;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String method = request.getMethod() == null ? null : request.getMethod().name();
        String path = request.getPath().pathWithinApplication().value();

        if (GatewayRouteMatcher.isInternal(path)) {
            return writeError(exchange, HttpStatus.NOT_FOUND, "not_found");
        }
        if (isPreflight(method)) {
            return chain.filter(exchange);
        }
        if (GatewayRouteMatcher.isPublic(method, path)) {
            return chain.filter(exchange);
        }

        ParsedToken token;
        try {
            token = accessTokenParser.parse(extractAccessToken(request));
        } catch (InvalidTokenException e) {
            return writeError(exchange, HttpStatus.UNAUTHORIZED, "unauthorized");
        }

        if (token.passwordChangeRequired() && !PasswordChangePolicy.isAllowed(method, path)) {
            return writeError(exchange, HttpStatus.FORBIDDEN, "password_change_required");
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        // Run before any routing filter; the exact value only needs to be lower
        // than NettyRoutingFilter's LOWEST_PRECEDENCE.
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }

    private static boolean isPreflight(String method) {
        return method != null && HttpMethod.OPTIONS.matches(method);
    }

    /**
     * Prefers the {@code access_token} cookie when present, otherwise falls back to
     * {@code Authorization: Bearer ...}. The order matches the shared servlet
     * resolver ({@code BearerOrCookieTokenResolver}) so a freshly rotated cookie
     * cannot be shadowed by a stale header, and the priority cannot drift between
     * the gateway and the services.
     */
    private static String extractAccessToken(ServerHttpRequest request) {
        var cookie = request.getCookies().getFirst(ACCESS_TOKEN_COOKIE);
        if (cookie != null && cookie.getValue() != null && !cookie.getValue().isBlank()) {
            return cookie.getValue();
        }
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            String value = authorization.substring(BEARER_PREFIX.length()).trim();
            if (!value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private static Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, String code) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = ("{\"error\":\"" + code + "\"}").getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(buffer));
    }
}
