package ru.nstu.system.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import ru.nstu.system.security.AccessTokenParser;
import ru.nstu.system.security.JwtProperties;
import ru.nstu.system.security.TokenIssuer;

/**
 * Unit tests for {@link GatewayAuthorizationFilter}. They exercise the filter
 * directly with {@link MockServerWebExchange}, without starting a gateway or any
 * downstream service.
 */
class GatewayAuthorizationFilterTest {

    private static final String SECRET = "unit-test-secret-long-enough-0123456789";

    private static final String OTHER_SECRET = "another-unit-secret-long-enough-987654321";

    private static final String ISSUER = "nstu-system";

    private static final String ACCESS_TOKEN_COOKIE = GatewayAuthorizationFilter.ACCESS_TOKEN_COOKIE;

    private JwtProperties properties;

    private TokenIssuer tokenIssuer;

    private GatewayAuthorizationFilter filter;

    @BeforeEach
    void setUp() {
        properties = properties(SECRET);
        tokenIssuer = new TokenIssuer(properties);
        filter = new GatewayAuthorizationFilter(new AccessTokenParser(properties));
    }

    @Test
    void forwardsRequestWithValidCookieToken() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/events/1")
                        .cookie(new HttpCookie(ACCESS_TOKEN_COOKIE, validToken()))
                        .build());

        filter.filter(exchange, recordingChain(invoked)).block();

        assertThat(invoked).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void acceptsBearerAuthorizationHeader() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/events/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken())
                        .build());

        filter.filter(exchange, recordingChain(invoked)).block();

        assertThat(invoked).isTrue();
    }

    @Test
    void cookieTokenTakesPrecedenceOverBearerHeader() {
        JwtProperties otherProperties = properties(OTHER_SECRET);
        String staleHeader = new TokenIssuer(otherProperties)
                .issueAccessToken("acc-1", Set.of("STUDENT"), false);
        AtomicBoolean invoked = new AtomicBoolean(false);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/events/1")
                        .cookie(new HttpCookie(ACCESS_TOKEN_COOKIE, validToken()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + staleHeader)
                        .build());

        filter.filter(exchange, recordingChain(invoked)).block();

        // The valid cookie must win over the forged header, otherwise the request
        // would be rejected even though a fresh session exists.
        assertThat(invoked).isTrue();
    }

    @Test
    void rejectsRequestWithoutToken() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/events/1").build());

        filter.filter(exchange, recordingChain(invoked)).block();

        assertThat(invoked).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("unauthorized");
    }

    @Test
    void rejectsForgedToken() {
        JwtProperties otherProperties = properties(OTHER_SECRET);
        String forged = new TokenIssuer(otherProperties)
                .issueAccessToken("acc-1", Set.of("STUDENT"), false);
        AtomicBoolean invoked = new AtomicBoolean(false);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/events/1")
                        .cookie(new HttpCookie(ACCESS_TOKEN_COOKIE, forged))
                        .build());

        filter.filter(exchange, recordingChain(invoked)).block();

        assertThat(invoked).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsExpiredToken() {
        Clock past = Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneOffset.UTC);
        String expired = new TokenIssuer(properties, past)
                .issueAccessToken("acc-1", Set.of("STUDENT"), false);
        AtomicBoolean invoked = new AtomicBoolean(false);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/events/1")
                        .cookie(new HttpCookie(ACCESS_TOKEN_COOKIE, expired))
                        .build());

        filter.filter(exchange, recordingChain(invoked)).block();

        assertThat(invoked).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void restrictedTokenIsForbiddenOnEvents() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        MockServerWebExchange exchange = restrictedExchange(MockServerHttpRequest.get("/api/events/1"));

        filter.filter(exchange, recordingChain(invoked)).block();

        assertThat(invoked).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("password_change_required");
    }

    @Test
    void restrictedTokenIsAllowedOnOwnProfile() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        MockServerWebExchange exchange = restrictedExchange(MockServerHttpRequest.get("/api/auth/me"));

        filter.filter(exchange, recordingChain(invoked)).block();

        assertThat(invoked).isTrue();
    }

    @Test
    void restrictedTokenIsAllowedOnPublicSite() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        MockServerWebExchange exchange = restrictedExchange(MockServerHttpRequest.get("/api/site"));

        filter.filter(exchange, recordingChain(invoked)).block();

        assertThat(invoked).isTrue();
    }

    @Test
    void publicLoginDoesNotRequireToken() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/login").build());

        filter.filter(exchange, recordingChain(invoked)).block();

        assertThat(invoked).isTrue();
    }

    @Test
    void internalPathIsHiddenWithNotFound() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/internal/students/1")
                        .cookie(new HttpCookie(ACCESS_TOKEN_COOKIE, validToken()))
                        .build());

        filter.filter(exchange, recordingChain(invoked)).block();

        assertThat(invoked).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("not_found");
    }

    @Test
    void corsPreflightPassesWithoutToken() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.options("/api/events/1")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .build());

        filter.filter(exchange, recordingChain(invoked)).block();

        assertThat(invoked).isTrue();
    }

    private MockServerWebExchange restrictedExchange(MockServerHttpRequest.BaseBuilder<?> request) {
        MockServerHttpRequest httpRequest = request
                .cookie(new HttpCookie(ACCESS_TOKEN_COOKIE, restrictedToken()))
                .build();
        return MockServerWebExchange.from(httpRequest);
    }

    private String validToken() {
        return tokenIssuer.issueAccessToken("acc-1", Set.of("STUDENT"), false);
    }

    private String restrictedToken() {
        return tokenIssuer.issueAccessToken("acc-1", Set.of("STUDENT"), true);
    }

    private static JwtProperties properties(String secret) {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret(secret);
        jwtProperties.setIssuer(ISSUER);
        return jwtProperties;
    }

    private static GatewayFilterChain recordingChain(AtomicBoolean invoked) {
        return exchange -> {
            invoked.set(true);
            exchange.getResponse().setStatusCode(HttpStatus.OK);
            return Mono.empty();
        };
    }
}
