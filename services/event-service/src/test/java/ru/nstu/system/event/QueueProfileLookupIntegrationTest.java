package ru.nstu.system.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import ru.nstu.system.contracts.Groups;
import ru.nstu.system.security.RoleNames;

/**
 * Task 8.1: the synchronous {@code student-service} lookup used when an account
 * joins without an explicit name (design.md D12).
 *
 * <p>A real HTTP stub runs on a free port via the JDK {@link HttpServer} — no
 * application class is mocked. The class contributes its own
 * {@link DynamicPropertySource}, so Spring builds a dedicated context pointing
 * {@code nstu.student-service.url} at the stub (the shared context keeps the
 * default URL and never calls out).</p>
 */
class QueueProfileLookupIntegrationTest extends AbstractEventIntegrationTest {

    private static final String STUB_PREFIX = "/internal/students/";

    private static final AtomicReference<String> LAST_INTERNAL_TOKEN = new AtomicReference<>();

    private static final Map<UUID, String> NAMES = new ConcurrentHashMap<>();

    private static final Map<UUID, Integer> STATUSES = new ConcurrentHashMap<>();

    private static final HttpServer STUB;

    private static final ExecutorService STUB_EXECUTOR;

    private static final int STUB_PORT;

    static {
        try {
            STUB = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            STUB_EXECUTOR = Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(runnable, "student-service-stub");
                thread.setDaemon(true);
                return thread;
            });
            STUB.setExecutor(STUB_EXECUTOR);
            STUB.createContext(STUB_PREFIX, QueueProfileLookupIntegrationTest::handle);
            STUB.start();
            STUB_PORT = STUB.getAddress().getPort();
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @DynamicPropertySource
    static void studentServiceUrl(DynamicPropertyRegistry registry) {
        registry.add("nstu.student-service.url", () -> "http://127.0.0.1:" + STUB_PORT);
    }

    @BeforeEach
    void resetStub() {
        NAMES.clear();
        STATUSES.clear();
        LAST_INTERNAL_TOKEN.set(null);
    }

    @AfterAll
    static void stopStub() {
        STUB.stop(0);
        STUB_EXECUTOR.shutdownNow();
    }

    @Test
    void accountWithoutNameGetsProfileNameAndSendsInternalToken() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Профиль"));
        UUID studentId = UUID.randomUUID();
        NAMES.put(studentId, "Петров Пётр");

        MvcResult result = joinQueue(eventId, tokenFor(studentId, RoleNames.STUDENT), null);

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        JsonNode body = responseJson(result);
        assertThat(body.get("name").asText()).isEqualTo("Петров Пётр");
        assertThat(body.get("holderAccountId").asText()).isEqualTo(studentId.toString());
        assertThat(LAST_INTERNAL_TOKEN.get()).isEqualTo(INTERNAL_TOKEN);
    }

    @Test
    void missingProfileMapsToProfileRequired() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Профиль"));
        UUID studentId = UUID.randomUUID();
        STATUSES.put(studentId, 404);

        MvcResult result = joinQueue(eventId, tokenFor(studentId, RoleNames.STUDENT), null);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(errorCode(result)).isEqualTo("profile_required");
        assertThat(activeEntryCount(eventId)).isZero();
    }

    @Test
    void brokenServiceMapsToProfileUnavailable() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Профиль"));
        UUID studentId = UUID.randomUUID();
        STATUSES.put(studentId, 500);

        MvcResult result = joinQueue(eventId, tokenFor(studentId, RoleNames.STUDENT), null);

        assertThat(result.getResponse().getStatus()).isEqualTo(503);
        assertThat(errorCode(result)).isEqualTo("profile_unavailable");
        assertThat(activeEntryCount(eventId)).isZero();
    }

    @Test
    void explicitNameSkipsTheProfileLookup() throws Exception {
        UUID eventId = createEvent(staffToken(), Map.of("title", "Профиль"));
        UUID studentId = UUID.randomUUID();
        STATUSES.put(studentId, 500);

        MvcResult result = joinQueue(eventId, tokenFor(studentId, RoleNames.STUDENT), "Своё имя");

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(responseJson(result).get("name").asText()).isEqualTo("Своё имя");
        assertThat(LAST_INTERNAL_TOKEN.get()).isNull();
    }

    private static void handle(HttpExchange exchange) throws IOException {
        LAST_INTERNAL_TOKEN.set(exchange.getRequestHeaders().getFirst("X-Internal-Token"));
        UUID accountId = parseAccountId(exchange.getRequestURI().getPath());
        int status = accountId == null ? 404 : STATUSES.getOrDefault(accountId, 200);

        try (InputStream requestBody = exchange.getRequestBody()) {
            requestBody.readAllBytes();
        }

        if (status == 200 && accountId != null) {
            String name = NAMES.getOrDefault(accountId, "Участник");
            byte[] payload = ("{\"accountId\":\"" + accountId
                    + "\",\"fullName\":\"" + name
                    + "\",\"groupId\":\"" + Groups.DEFAULT_GROUP_ID + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            try (OutputStream responseBody = exchange.getResponseBody()) {
                responseBody.write(payload);
            }
        } else {
            byte[] payload = ("{\"error\":\"stub\",\"status\":" + status + "}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, payload.length);
            try (OutputStream responseBody = exchange.getResponseBody()) {
                responseBody.write(payload);
            }
        }
    }

    private static UUID parseAccountId(String path) {
        if (path == null || !path.startsWith(STUB_PREFIX)) {
            return null;
        }
        try {
            return UUID.fromString(path.substring(STUB_PREFIX.length()));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
