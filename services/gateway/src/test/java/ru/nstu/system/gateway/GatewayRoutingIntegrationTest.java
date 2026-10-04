package ru.nstu.system.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import ru.nstu.system.gateway.security.GatewayAuthorizationFilter;
import ru.nstu.system.security.TokenIssuer;

/**
 * End-to-end test of the gateway filter chain against a stub downstream service.
 *
 * <p>The stub is a Reactor Netty server bound to a random loopback port; the
 * {@code /api/events/**} route is redirected to it through a dynamic property, so
 * the test never depends on a running event-service or any other external
 * process. A real access token is issued by {@link TokenIssuer} from the shared
 * security module.</p>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "nstu.jwt.secret=integration-test-secret-long-enough-0123456789",
                "nstu.cors.allowed-origins=http://localhost:5173"
        })
class GatewayRoutingIntegrationTest {

    private static final String STUB_PATH = "/api/events/ping";

    private static final String CALENDAR_PATH = "/api/calendar?from=2026-10-05&to=2026-10-18";

    private static final String NOTES_PATH = "/api/notes";

    private static final String STUB_BODY = "pong";

    private static final AtomicReference<String> LAST_COOKIE = new AtomicReference<>();

    private static final AtomicReference<String> LAST_AUTHORIZATION = new AtomicReference<>();

    private static final DisposableServer STUB;

    static {
        STUB = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .handle((request, response) -> {
                    LAST_COOKIE.set(request.requestHeaders().get(HttpHeaders.COOKIE));
                    LAST_AUTHORIZATION.set(request.requestHeaders().get(HttpHeaders.AUTHORIZATION));
                    return response.status(200)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                            .sendString(Mono.just(STUB_BODY));
                })
                .bindNow();
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TokenIssuer tokenIssuer;

    private WebTestClient client;

    @DynamicPropertySource
    static void downstreamRoute(DynamicPropertyRegistry registry) {
        registry.add("nstu.routes.event-uri", () -> "http://127.0.0.1:" + STUB.port());
        // /api/notes is served by student-service; point it at the same stub.
        registry.add("nstu.routes.student-uri", () -> "http://127.0.0.1:" + STUB.port());
        // /api/site is served by auth-service; point it at the same stub.
        registry.add("nstu.routes.auth-uri", () -> "http://127.0.0.1:" + STUB.port());
    }

    @BeforeEach
    void setUp() {
        LAST_COOKIE.set(null);
        LAST_AUTHORIZATION.set(null);
        client = WebTestClient.bindToServer()
                .baseUrl("http://127.0.0.1:" + port)
                .build();
    }

    @AfterAll
    static void stopStub() {
        STUB.disposeNow();
    }

    @Test
    void proxiesRequestWithValidCookieTokenAndPreservesIt() {
        String token = accessToken();

        client.get().uri(STUB_PATH)
                .cookie(GatewayAuthorizationFilter.ACCESS_TOKEN_COOKIE, token)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Content-Type-Options", "nosniff")
                .expectHeader().valueEquals("Referrer-Policy", "no-referrer")
                .expectBody(String.class).isEqualTo(STUB_BODY);

        assertThat(LAST_COOKIE.get()).contains("access_token=" + token);
    }

    @Test
    void forwardsAuthorizationHeaderUnchanged() {
        String token = accessToken();

        client.get().uri(STUB_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo(STUB_BODY);

        assertThat(LAST_AUTHORIZATION.get()).isEqualTo("Bearer " + token);
    }

    @Test
    void rejectsRequestWithoutToken() {
        client.get().uri(STUB_PATH)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("X-Content-Type-Options", "nosniff")
                .expectBody().jsonPath("$.error").isEqualTo("unauthorized");
    }

    @Test
    void rejectsCalendarRequestWithoutToken() {
        client.get().uri(CALENDAR_PATH)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.error").isEqualTo("unauthorized");
    }

    @Test
    void proxiesCalendarRequestWithValidToken() {
        String token = accessToken();

        client.get().uri(CALENDAR_PATH)
                .cookie(GatewayAuthorizationFilter.ACCESS_TOKEN_COOKIE, token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo(STUB_BODY);
    }

    @Test
    void rejectsNotesRequestWithoutToken() {
        client.get().uri(NOTES_PATH)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.error").isEqualTo("unauthorized");
    }

    @Test
    void proxiesNotesRequestWithValidToken() {
        String token = accessToken();

        client.get().uri(NOTES_PATH)
                .cookie(GatewayAuthorizationFilter.ACCESS_TOKEN_COOKIE, token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo(STUB_BODY);
    }

    @Test
    void proxiesPublicSiteWithoutToken() {
        client.get().uri("/api/site")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo(STUB_BODY);
    }

    @Test
    void proxiesPublicSiteIconWithoutToken() {
        client.get().uri("/api/site/icon")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo(STUB_BODY);
    }

    @Test
    void restrictedTokenIsAllowedOnPublicSiteIcon() {
        String restricted = tokenIssuer.issueAccessToken("acc-1", Set.of("STUDENT"), true);

        client.get().uri("/api/site/icon")
                .cookie(GatewayAuthorizationFilter.ACCESS_TOKEN_COOKIE, restricted)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo(STUB_BODY);
    }

    @Test
    void adminSiteIconWriteWithoutTokenIsUnauthorized() {
        client.put().uri("/api/admin/site/icon")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.error").isEqualTo("unauthorized");
    }

    @Test
    void restrictedTokenIsForbiddenOnEventsButAllowedOnPublicSite() {
        String restricted = tokenIssuer.issueAccessToken("acc-1", Set.of("STUDENT"), true);

        client.get().uri(STUB_PATH)
                .cookie(GatewayAuthorizationFilter.ACCESS_TOKEN_COOKIE, restricted)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody().jsonPath("$.error").isEqualTo("password_change_required");

        client.get().uri("/api/site")
                .cookie(GatewayAuthorizationFilter.ACCESS_TOKEN_COOKIE, restricted)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo(STUB_BODY);
    }

    @Test
    void hidesInternalPathsFromOutside() {
        // No route matches /internal/**, so the reactive stack answers 404 before
        // any routing happens (the explicit GlobalFilter check covers the case of
        // a future catch-all route).
        client.get().uri("/internal/students/1")
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().valueEquals("X-Content-Type-Options", "nosniff");
    }

    @Test
    void answersCorsPreflightWithoutToken() {
        client.method(HttpMethod.OPTIONS).uri(STUB_PATH)
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173")
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
    }

    private String accessToken() {
        return tokenIssuer.issueAccessToken("acc-1", Set.of("STUDENT"), false);
    }
}
