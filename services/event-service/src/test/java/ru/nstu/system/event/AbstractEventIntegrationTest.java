package ru.nstu.system.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ru.nstu.system.contracts.Groups;
import ru.nstu.system.event.service.EventArchiveService;
import ru.nstu.system.security.RoleNames;
import ru.nstu.system.security.TokenIssuer;

/**
 * Shared setup for the {@code event-service} web integration tests (tasks
 * 7.1-7.7).
 *
 * <p>One Spring context and one PostgreSQL 16 container are reused by all
 * subclasses (identical dynamic properties keep Boot's context cache effective).
 * Tests are not transactional: the service commits its own transactions, so state
 * is reset explicitly before every test. No broker is required — the outbox poll
 * interval is pushed to 24 hours and only committed outbox rows are asserted.</p>
 */
@SpringBootTest(properties = {
        "nstu.jwt.secret=nstu-integration-test-secret-0123456789",
        "nstu.outbox.poll-interval=PT24H",
        // Keep the retention sweep far away so it never races the assertions; the
        // sweep is exercised deterministically through archiveService.sweepOnce().
        "nstu.archive.sweep-interval=PT24H",
        // Non-empty so the student-service HTTP stub can assert the header value.
        "nstu.internal.token=test-internal-token"
})
@AutoConfigureMockMvc
abstract class AbstractEventIntegrationTest {

    /** Value of {@code X-Internal-Token} expected by the profile-lookup stub. */
    protected static final String INTERNAL_TOKEN = "test-internal-token";

    /** Default base URL; the profile-lookup test overrides it with a real stub. */
    protected static final String DEFAULT_STUDENT_SERVICE_URL = "http://localhost:8082";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected TokenIssuer tokenIssuer;

    /** Used to trigger the retention sweep deterministically (task 9.5). */
    @Autowired
    protected EventArchiveService archiveService;

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", EventTestContainers.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", EventTestContainers.POSTGRES::getUsername);
        registry.add("spring.datasource.password", EventTestContainers.POSTGRES::getPassword);
    }

    @BeforeEach
    void resetState() {
        jdbcTemplate.update("delete from event.queue_entry");
        jdbcTemplate.update("delete from event.event");
        jdbcTemplate.update("delete from event.calendar_entry");
        jdbcTemplate.update("delete from event.outbox");
    }

    // ------------------------------------------------------------------
    // Token helpers
    // ------------------------------------------------------------------

    protected String tokenFor(UUID accountId, String role) {
        return tokenIssuer.issueAccessToken(accountId.toString(), Set.of(role), false);
    }

    protected String staffToken(UUID accountId) {
        return tokenFor(accountId, RoleNames.STAFF);
    }

    protected String staffToken() {
        return staffToken(UUID.randomUUID());
    }

    protected String adminToken() {
        return tokenFor(UUID.randomUUID(), RoleNames.ADMIN);
    }

    protected String studentToken() {
        return tokenFor(UUID.randomUUID(), RoleNames.STUDENT);
    }

    protected String guestToken() {
        return tokenIssuer.issueAccessToken("guest:" + UUID.randomUUID(), Set.of(RoleNames.GUEST), false);
    }

    protected static MockHttpServletRequestBuilder authorized(
            MockHttpServletRequestBuilder builder, String token) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    protected String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    // ------------------------------------------------------------------
    // API helpers
    // ------------------------------------------------------------------

    /** Performs {@code POST /api/events} and returns the raw result. */
    protected MvcResult createEventResult(String token, Map<String, Object> body) throws Exception {
        return mockMvc.perform(authorized(post("/api/events"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andReturn();
    }

    /** Creates an event and returns its id, asserting a 201 response. */
    protected UUID createEvent(String token, Map<String, Object> body) throws Exception {
        MvcResult result = createEventResult(token, body);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return eventId(result);
    }

    protected UUID eventId(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    // ------------------------------------------------------------------
    // Queue API helpers (tasks 8.1-8.8)
    // ------------------------------------------------------------------

    /** Performs {@code POST /api/events/{id}/queue}; a {@code null} name is sent as {@code {}}. */
    protected MvcResult joinQueue(UUID eventId, String token, String name) throws Exception {
        Map<String, Object> body = name == null ? Map.of() : Map.of("name", name);
        return mockMvc.perform(authorized(post("/api/events/{id}/queue", eventId), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andReturn();
    }

    /** Joins the queue and returns the new entry id, asserting a 201 response. */
    protected UUID joinQueueOk(UUID eventId, String token, String name) throws Exception {
        MvcResult result = joinQueue(eventId, token, name);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return entryId(result);
    }

    /** Performs {@code GET /api/events/{id}/queue}. */
    protected MvcResult getQueue(UUID eventId, String token) throws Exception {
        return mockMvc.perform(authorized(get("/api/events/{id}/queue", eventId), token))
                .andReturn();
    }

    /** Performs {@code GET /api/events/{id}/queue} with an {@code If-None-Match} header. */
    protected MvcResult getQueue(UUID eventId, String token, String ifNoneMatch) throws Exception {
        return mockMvc.perform(authorized(get("/api/events/{id}/queue", eventId), token)
                        .header(HttpHeaders.IF_NONE_MATCH, ifNoneMatch))
                .andReturn();
    }

    /** Performs {@code POST /api/events/{id}/queue/advance}. */
    protected MvcResult advance(UUID eventId, String token) throws Exception {
        return mockMvc.perform(authorized(post("/api/events/{id}/queue/advance", eventId), token))
                .andReturn();
    }

    /** Performs {@code POST /api/events/{id}/queue/{entryId}/pause}. */
    protected MvcResult pause(UUID eventId, UUID entryId, String token) throws Exception {
        return mockMvc.perform(
                        authorized(post("/api/events/{id}/queue/{entryId}/pause", eventId, entryId), token))
                .andReturn();
    }

    /** Performs {@code POST /api/events/{id}/queue/{entryId}/resume}. */
    protected MvcResult resume(UUID eventId, UUID entryId, String token) throws Exception {
        return mockMvc.perform(
                        authorized(post("/api/events/{id}/queue/{entryId}/resume", eventId, entryId), token))
                .andReturn();
    }

    /** Performs {@code PATCH /api/events/{id}/queue/{entryId}/position}. */
    protected MvcResult reorder(UUID eventId, UUID entryId, int position, String token) throws Exception {
        return mockMvc.perform(
                        authorized(patch("/api/events/{id}/queue/{entryId}/position", eventId, entryId), token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("position", position))))
                .andReturn();
    }

    /** Performs {@code DELETE /api/events/{id}/queue/{entryId}}. */
    protected MvcResult deleteEntry(UUID eventId, UUID entryId, String token) throws Exception {
        return mockMvc.perform(
                        authorized(delete("/api/events/{id}/queue/{entryId}", eventId, entryId), token))
                .andReturn();
    }

    /** Extracts the {@code id} of the entry DTO returned by a 201 join response. */
    protected UUID entryId(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    // ------------------------------------------------------------------
    // Event detail / history / archive (tasks 9.1-9.8)
    // ------------------------------------------------------------------

    /** Performs {@code GET /api/events/{id}} (event detail with journal). */
    protected MvcResult getEvent(UUID eventId, String token) throws Exception {
        return mockMvc.perform(authorized(get("/api/events/{id}", eventId), token)).andReturn();
    }

    /** Performs {@code GET /api/events/history}. */
    protected MvcResult getHistory(String token) throws Exception {
        return mockMvc.perform(authorized(get("/api/events/history"), token)).andReturn();
    }

    /** Performs {@code POST /api/events/{id}/close}. */
    protected MvcResult closeEvent(UUID eventId, String token) throws Exception {
        return mockMvc.perform(authorized(post("/api/events/{id}/close", eventId), token)).andReturn();
    }

    /** Performs {@code POST /api/events/{id}/archive}. */
    protected MvcResult archiveEvent(UUID eventId, String token) throws Exception {
        return mockMvc.perform(authorized(post("/api/events/{id}/archive", eventId), token)).andReturn();
    }

    /** Performs {@code POST /api/events/{id}/restore}. */
    protected MvcResult restoreEvent(UUID eventId, String token) throws Exception {
        return mockMvc.perform(authorized(post("/api/events/{id}/restore", eventId), token)).andReturn();
    }

    /** Performs {@code DELETE /api/events/{id}}. */
    protected MvcResult deleteEvent(UUID eventId, String token) throws Exception {
        return mockMvc.perform(authorized(delete("/api/events/{id}", eventId), token)).andReturn();
    }

    /** Performs {@code POST /api/events/{id}/queue/staff}; a {@code null} name is sent as {@code {}}. */
    protected MvcResult addStaffEntry(UUID eventId, String token, String name) throws Exception {
        Map<String, Object> body = name == null ? Map.of() : Map.of("name", name);
        return mockMvc.perform(authorized(post("/api/events/{id}/queue/staff", eventId), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andReturn();
    }

    /** Performs {@code POST /api/events/{targetId}/carry-over}. */
    protected MvcResult carryOver(UUID targetId, String token, Map<String, Object> body) throws Exception {
        return mockMvc.perform(authorized(post("/api/events/{id}/carry-over", targetId), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andReturn();
    }

    /** Closes and then archives an event, asserting both responses. */
    protected void archiveClosedEvent(UUID eventId, String token) throws Exception {
        assertThat(closeEvent(eventId, token).getResponse().getStatus()).isEqualTo(200);
        assertThat(archiveEvent(eventId, token).getResponse().getStatus()).isEqualTo(200);
    }

    /** @return number of stored (active and passed) queue rows of an event */
    protected int queueRowCount(UUID eventId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from event.queue_entry where event_id = ?", Integer.class, eventId);
        return count == null ? 0 : count;
    }

    /** @return number of committed outbox rows with the given {@code event_type} */
    protected int outboxCountByType(String eventType) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from event.outbox where event_type = ?", Integer.class, eventType);
        return count == null ? 0 : count;
    }

    /** @return the {@code ETag} response header, or {@code null} when absent */
    protected String etag(MvcResult result) {
        return result.getResponse().getHeader(HttpHeaders.ETAG);
    }

    /** @return the {@code error} code of a JSON error body, or {@code null} when absent */
    protected String errorCode(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        if (body.isEmpty()) {
            return null;
        }
        var node = objectMapper.readTree(body).get("error");
        return node == null ? null : node.asText();
    }

    /** @return the JSON body of a response, parsed as a tree */
    protected JsonNode responseJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    // ------------------------------------------------------------------
    // Persistence helpers
    // ------------------------------------------------------------------

    /** Inserts an event row directly, bypassing the API (for archived/other-group cases). */
    protected UUID insertEvent(String availability, String status) {
        return insertEvent(Groups.DEFAULT_GROUP_ID, availability, status);
    }

    protected UUID insertEvent(UUID groupId, String availability, String status) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into event.event (id, group_id, title, type, availability, slug, status, created_by) "
                        + "values (?, ?, ?, 'QUEUE', ?, ?, ?, ?)",
                id, groupId, "Событие из SQL", availability, "sql-" + id, status, UUID.randomUUID());
        return id;
    }

    protected void insertActiveEntry(UUID eventId, String name, int position, String status) {
        jdbcTemplate.update(
                "insert into event.queue_entry (id, event_id, name, name_normalized, position, status) "
                        + "values (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), eventId, name, name.toLowerCase(Locale.ROOT), position, status);
    }

    protected int activeEntryCount(UUID eventId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from event.queue_entry where event_id = ? and status in ('WAITING','PAUSED')",
                Integer.class, eventId);
        return count == null ? 0 : count;
    }

    protected int outboxCount() {
        Integer count = jdbcTemplate.queryForObject("select count(*) from event.outbox", Integer.class);
        return count == null ? 0 : count;
    }
}
