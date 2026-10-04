package ru.nstu.system.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.nstu.system.contracts.Groups;
import ru.nstu.system.contracts.events.EventTypes;
import ru.nstu.system.e2e.support.ApiClient;
import ru.nstu.system.e2e.support.ApiResponse;
import ru.nstu.system.e2e.support.RabbitEventProbe;
import ru.nstu.system.e2e.support.ServiceCluster;

/**
 * Cross-service end-to-end suite for the event-queue MVP (OpenSpec change
 * add-event-queue-mvp, task 12.1; design.md D1, D11, D12, D13, D21).
 *
 * <p>One PostgreSQL 16 container, one RabbitMQ container and the three real
 * service processes are started once for the class. The suite then exercises the
 * complete journey over HTTP with cookie-based sessions:</p>
 *
 * <ol>
 *   <li>first start of auth-service bootstraps exactly one administrator and
 *       stores only a password hash;</li>
 *   <li>the administrator logs in, is flagged for a mandatory password change and
 *       has no access to {@code /api/events} until the change is done;</li>
 *   <li>the password change lifts the flag, revokes the old refresh token and the
 *       new password works;</li>
 *   <li>a {@code STAFF} account is created; {@code account.created} travels through
 *       the transactional outbox to RabbitMQ and student-service provisions the
 *       profile asynchronously;</li>
 *   <li>the staff member logs in, changes the password and creates an event;</li>
 *   <li>a guest opens the short link, joins the queue, then staff presses "next";</li>
 *   <li>{@code entry.passed}/{@code queue.advanced} reach RabbitMQ;</li>
 *   <li>the queue tail is carried over into a second event with
 *       {@code origin = CARRY_OVER};</li>
 *   <li>the event is closed, archived, hidden from the active list and the short
 *       link, then restored by the administrator back to {@code CLOSED} with its
 *       rows;</li>
 *   <li>{@code event.closed} and {@code event.archived} are published to
 *       RabbitMQ.</li>
 * </ol>
 */
class MvpEndToEndTest {

    private static final String ADMIN_NEW_PASSWORD = "Admin-Changed1";

    private static final String STAFF_USERNAME = "staff1";

    private static final String STAFF_DISPLAY_NAME = "Иванов Иван Иванович";

    private static final String STAFF_NEW_PASSWORD = "Staff-Changed1";

    private static final Duration ASYNC_TIMEOUT = Duration.ofSeconds(30);

    private static ServiceCluster cluster;

    private static RabbitEventProbe probe;

    @BeforeAll
    static void startEnvironment() {
        cluster = new ServiceCluster();
        // Start the broker probe before the services so no published event is missed.
        probe = new RabbitEventProbe(
                cluster.rabbitHost(), cluster.rabbitPort(), cluster.rabbitUsername(), cluster.rabbitPassword());
        cluster.startApplications();
        cluster.awaitReady();
    }

    @AfterAll
    static void stopEnvironment() {
        if (probe != null) {
            probe.close();
        }
        if (cluster != null) {
            cluster.close();
        }
    }

    @Test
    @DisplayName("First start creates exactly one administrator and stores only a hash")
    void adminBootstrapCreatesSingleAdminAndStoresOnlyHash() throws Exception {
        try (Connection connection = cluster.openConnection();
                Statement statement = connection.createStatement()) {

            try (ResultSet count = statement.executeQuery(
                    "select count(*) from auth.account where role = 'ADMIN'")) {
                count.next();
                assertThat(count.getInt(1)).isEqualTo(1);
            }

            try (ResultSet hash = statement.executeQuery(
                    "select password_hash from auth.account where role = 'ADMIN'")) {
                hash.next();
                String passwordHash = hash.getString(1);
                assertThat(passwordHash).startsWith("$2");
                assertThat(passwordHash).isNotEqualTo(ServiceCluster.ADMIN_PASSWORD);
            }
        }
    }

    @Test
    @DisplayName("Full MVP journey: bootstrap → staff → event → guest queue → advance → carry-over → archive → restore")
    void fullMvpJourney() {
        // ------------------------------------------------------------------
        // Check 2 — admin login is restricted until the password is changed
        // ------------------------------------------------------------------
        ApiClient adminAuth = new ApiClient(cluster.authBaseUrl());
        ApiResponse adminLogin = adminAuth.post("/api/auth/login", Map.of(
                "username", ServiceCluster.ADMIN_USERNAME,
                "password", ServiceCluster.ADMIN_PASSWORD));
        assertThat(adminLogin.status()).isEqualTo(200);
        assertThat(adminLogin.json().path("mustChangePassword").asBoolean()).isTrue();
        assertThat(adminAuth.cookie("access_token")).isNotBlank();
        assertThat(adminAuth.cookie("refresh_token")).isNotBlank();
        String initialRefreshToken = adminAuth.cookie("refresh_token");

        ApiClient adminEvents = adminAuth.on(cluster.eventBaseUrl());
        assertThat(adminEvents.get("/api/events").status())
                .as("restricted token must not reach /api/events")
                .isEqualTo(403);

        // ------------------------------------------------------------------
        // Check 3 — password change lifts the flag and revokes the old refresh
        // ------------------------------------------------------------------
        ApiResponse adminPasswordChange = adminAuth.post("/api/auth/password", Map.of(
                "oldPassword", ServiceCluster.ADMIN_PASSWORD,
                "newPassword", ADMIN_NEW_PASSWORD));
        assertThat(adminPasswordChange.status()).isEqualTo(200);
        assertThat(adminPasswordChange.json().path("mustChangePassword").asBoolean()).isFalse();

        ApiClient staleRefresh = new ApiClient(cluster.authBaseUrl());
        staleRefresh.setCookie("refresh_token", initialRefreshToken);
        assertThat(staleRefresh.post("/api/auth/refresh", null).status())
                .as("the pre-change refresh token must be revoked")
                .isEqualTo(401);

        ApiClient adminRelogin = new ApiClient(cluster.authBaseUrl());
        assertThat(adminRelogin.post("/api/auth/login", Map.of(
                "username", ServiceCluster.ADMIN_USERNAME,
                "password", ServiceCluster.ADMIN_PASSWORD)).status())
                .as("the initial password must no longer work")
                .isEqualTo(401);
        ApiResponse relogin = adminRelogin.post("/api/auth/login", Map.of(
                "username", ServiceCluster.ADMIN_USERNAME,
                "password", ADMIN_NEW_PASSWORD));
        assertThat(relogin.status()).isEqualTo(200);
        assertThat(relogin.json().path("mustChangePassword").asBoolean()).isFalse();

        assertThat(adminEvents.get("/api/events").status())
                .as("the full token must allow /api/events")
                .isEqualTo(200);

        // ------------------------------------------------------------------
        // Check 4 — create STAFF; account.created reaches student-service
        // ------------------------------------------------------------------
        ApiResponse createdUser = adminAuth.post("/api/users", Map.of(
                "username", STAFF_USERNAME,
                "displayName", STAFF_DISPLAY_NAME,
                "role", "STAFF",
                "email", "staff1@example.com"));
        assertThat(createdUser.status()).isEqualTo(201);
        JsonNode staff = createdUser.json();
        assertThat(staff.path("mustChangePassword").asBoolean()).isTrue();
        String staffId = staff.path("id").asText();
        String temporaryPassword = staff.path("temporaryPassword").asText();
        assertThat(temporaryPassword).isNotBlank();

        String internalToken = ServiceCluster.INTERNAL_TOKEN;
        Awaitility.await().atMost(ASYNC_TIMEOUT).untilAsserted(() ->
                assertThat(probe.hasEvent(EventTypes.ACCOUNT_CREATED))
                        .as("account.created must be published to RabbitMQ")
                        .isTrue());

        Awaitility.await().atMost(ASYNC_TIMEOUT).untilAsserted(() -> {
            ApiResponse profile;
            try {
                profile = getInternalProfile(staffId, internalToken);
            } catch (RuntimeException ex) {
                throw new AssertionError("internal profile call failed", ex);
            }
            assertThat(profile.status()).isEqualTo(200);
            assertThat(profile.json().path("fullName").asText()).isEqualTo(STAFF_DISPLAY_NAME);
            assertThat(profile.json().path("groupId").asText())
                    .isEqualTo(Groups.DEFAULT_GROUP_ID.toString());
        });

        // ------------------------------------------------------------------
        // Check 5 — staff logs in, changes password, creates the event
        // ------------------------------------------------------------------
        ApiClient staffAuth = new ApiClient(cluster.authBaseUrl());
        ApiResponse staffLogin = staffAuth.post("/api/auth/login", Map.of(
                "username", STAFF_USERNAME,
                "password", temporaryPassword));
        assertThat(staffLogin.status()).isEqualTo(200);
        assertThat(staffLogin.json().path("mustChangePassword").asBoolean()).isTrue();

        ApiResponse staffPasswordChange = staffAuth.post("/api/auth/password", Map.of(
                "oldPassword", temporaryPassword,
                "newPassword", STAFF_NEW_PASSWORD));
        assertThat(staffPasswordChange.status()).isEqualTo(200);
        assertThat(staffPasswordChange.json().path("mustChangePassword").asBoolean()).isFalse();

        ApiClient staffEvents = staffAuth.on(cluster.eventBaseUrl());
        ApiResponse createdEvent = staffEvents.post("/api/events", Map.of("title", "Очередь 1"));
        assertThat(createdEvent.status()).isEqualTo(201);
        String event1Id = createdEvent.json().path("id").asText();
        String event1Slug = createdEvent.json().path("slug").asText();
        assertThat(event1Slug).isNotBlank();

        // ------------------------------------------------------------------
        // Check 6 — guest opens the short link and joins the queue
        // ------------------------------------------------------------------
        ApiClient anonymous = new ApiClient(cluster.eventBaseUrl());
        assertThat(anonymous.get("/api/events/by-slug/" + event1Slug).status())
                .as("the short link must require a session")
                .isEqualTo(401);

        ApiClient guest1 = new ApiClient(cluster.authBaseUrl());
        ApiResponse guestSession = guest1.post("/api/auth/guest", null);
        assertThat(guestSession.status()).isEqualTo(200);
        assertThat(guestSession.json().path("guest").asBoolean()).isTrue();
        ApiClient guest1Events = guest1.on(cluster.eventBaseUrl());

        ApiResponse bySlug = guest1Events.get("/api/events/by-slug/" + event1Slug);
        assertThat(bySlug.status()).isEqualTo(200);
        assertThat(bySlug.json().path("title").asText()).isEqualTo("Очередь 1");
        assertThat(bySlug.json().has("queue"))
                .as("the short link must never expose queue state")
                .isFalse();

        ApiResponse join1 = guest1Events.post(
                "/api/events/" + event1Id + "/queue", Map.of("name", "Гость-1"));
        assertThat(join1.status()).isEqualTo(201);
        assertThat(join1.json().path("status").asText()).isEqualTo("WAITING");
        assertThat(join1.json().path("position").asInt()).isEqualTo(1);
        assertThat(join1.json().path("guestRef").asText()).isNotBlank();

        // ------------------------------------------------------------------
        // Check 7 — staff advances the queue; events reach RabbitMQ
        // ------------------------------------------------------------------
        ApiResponse advance = staffEvents.post("/api/events/" + event1Id + "/queue/advance", null);
        assertThat(advance.status()).isEqualTo(200);
        JsonNode journal = advance.json().path("journal");
        assertThat(journal.isArray()).isTrue();
        assertThat(journal.size()).isEqualTo(1);
        assertThat(journal.get(0).path("status").asText()).isEqualTo("PASSED");
        assertThat(journal.get(0).path("passedAt").isNull()).isFalse();

        JsonNode stateAfterAdvance = staffEvents.get("/api/events/" + event1Id + "/queue").json();
        assertThat(stateAfterAdvance.path("queue").size()).isZero();
        assertThat(stateAfterAdvance.path("journal").size()).isEqualTo(1);

        Awaitility.await().atMost(ASYNC_TIMEOUT).untilAsserted(() -> {
            assertThat(probe.hasEvent(EventTypes.ENTRY_PASSED)).isTrue();
            assertThat(probe.hasEvent(EventTypes.QUEUE_ADVANCED)).isTrue();
        });
        assertThat(probe.first(EventTypes.ENTRY_PASSED).orElseThrow().json()
                .path("payload").path("name").asText()).isEqualTo("Гость-1");

        // ------------------------------------------------------------------
        // Check 8 — carry the tail over into a second event
        // ------------------------------------------------------------------
        ApiClient guest2 = new ApiClient(cluster.authBaseUrl());
        assertThat(guest2.post("/api/auth/guest", null).status()).isEqualTo(200);
        ApiClient guest2Events = guest2.on(cluster.eventBaseUrl());
        ApiResponse join2 = guest2Events.post(
                "/api/events/" + event1Id + "/queue", Map.of("name", "Гость-2"));
        assertThat(join2.status()).isEqualTo(201);
        assertThat(join2.json().path("status").asText()).isEqualTo("WAITING");

        ApiResponse createdEvent2 = staffEvents.post("/api/events", Map.of("title", "Очередь 2"));
        assertThat(createdEvent2.status()).isEqualTo(201);
        String event2Id = createdEvent2.json().path("id").asText();

        ApiResponse carryOver = staffEvents.post(
                "/api/events/" + event2Id + "/carry-over", Map.of("sourceEventId", event1Id));
        assertThat(carryOver.status()).isEqualTo(200);
        assertThat(carryOver.json().path("added").size()).isEqualTo(1);
        assertThat(carryOver.json().path("skipped").size()).isZero();
        assertThat(carryOver.json().path("added").get(0).path("name").asText()).isEqualTo("Гость-2");

        JsonNode targetQueue = staffEvents.get("/api/events/" + event2Id + "/queue").json().path("queue");
        assertThat(targetQueue.size()).isEqualTo(1);
        assertThat(targetQueue.get(0).path("name").asText()).isEqualTo("Гость-2");
        assertThat(targetQueue.get(0).path("origin").asText()).isEqualTo("CARRY_OVER");

        // ------------------------------------------------------------------
        // Check 9 — close, archive, hide, then restore as administrator
        // ------------------------------------------------------------------
        ApiResponse closed = staffEvents.post("/api/events/" + event1Id + "/close", null);
        assertThat(closed.status()).isEqualTo(200);
        assertThat(closed.json().path("status").asText()).isEqualTo("CLOSED");

        ApiResponse archived = staffEvents.post("/api/events/" + event1Id + "/archive", null);
        assertThat(archived.status()).isEqualTo(200);
        assertThat(archived.json().path("status").asText()).isEqualTo("ARCHIVED");

        assertThat(guest1Events.get("/api/events/by-slug/" + event1Slug).status())
                .as("an archived short link must be 404 for everyone")
                .isEqualTo(404);

        JsonNode activeList = staffEvents.get("/api/events").json();
        assertThat(containsId(activeList, event1Id))
                .as("an archived event must not appear in the active list")
                .isFalse();

        ApiClient adminEventsFull = adminAuth.on(cluster.eventBaseUrl());
        ApiResponse history = adminEventsFull.get("/api/events/history");
        assertThat(history.status()).isEqualTo(200);
        JsonNode archivedRow = findById(history.json(), event1Id);
        assertThat(archivedRow).isNotNull();
        assertThat(archivedRow.path("status").asText()).isEqualTo("ARCHIVED");

        ApiResponse restored = adminEventsFull.post("/api/events/" + event1Id + "/restore", null);
        assertThat(restored.status()).isEqualTo(200);
        assertThat(restored.json().path("status").asText()).isEqualTo("CLOSED");

        JsonNode restoredState = staffEvents.get("/api/events/" + event1Id + "/queue").json();
        assertThat(restoredState.path("queue").size()).isEqualTo(1);
        assertThat(restoredState.path("queue").get(0).path("name").asText()).isEqualTo("Гость-2");
        assertThat(restoredState.path("journal").size()).isEqualTo(1);
        assertThat(restoredState.path("journal").get(0).path("name").asText()).isEqualTo("Гость-1");

        // ------------------------------------------------------------------
        // Check 10 — closed/archived lifecycle events reached RabbitMQ
        // ------------------------------------------------------------------
        Awaitility.await().atMost(ASYNC_TIMEOUT).untilAsserted(() -> {
            assertThat(probe.hasEvent(EventTypes.EVENT_CLOSED)).isTrue();
            assertThat(probe.hasEvent(EventTypes.EVENT_ARCHIVED)).isTrue();
        });
        assertThat(probe.first(EventTypes.EVENT_ARCHIVED).orElseThrow().json()
                .path("payload").path("eventId").asText()).isEqualTo(event1Id);
    }

    private static ApiResponse getInternalProfile(String accountId, String internalToken) {
        return new ApiClient(cluster.studentBaseUrl())
                .get("/internal/students/" + accountId, Map.of("X-Internal-Token", internalToken));
    }

    private static boolean containsId(JsonNode array, String id) {
        for (JsonNode node : array) {
            if (node.path("id").asText().equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static JsonNode findById(JsonNode array, String id) {
        for (JsonNode node : array) {
            if (node.path("id").asText().equals(id)) {
                return node;
            }
        }
        return null;
    }
}
