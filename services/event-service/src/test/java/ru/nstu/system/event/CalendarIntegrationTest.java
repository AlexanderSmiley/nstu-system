package ru.nstu.system.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import ru.nstu.system.contracts.Groups;
import ru.nstu.system.event.domain.Audience;
import ru.nstu.system.security.RoleNames;

/**
 * Integration tests of the calendar module (change add-calendar-module;
 * design.md D3, D4, D7): role-based visibility in SQL, audience permissions,
 * window bounds, creation and the deletion matrix.
 *
 * <p>The author snapshot and {@code PATCH} behaviour
 * (change add-preferences-and-calendar-ui; design.md D5, D6) run against a
 * dedicated {@code student-service} HTTP stub, so the profile lookup is
 * deterministic: an account present in {@link #PROFILE_NAMES} yields a name, any
 * other account a {@code 404}. Pointing the shared context at the default
 * {@code localhost:8082} would make the "no name" case depend on whatever runs
 * on the developer's machine.</p>
 */
class CalendarIntegrationTest extends AbstractEventIntegrationTest {

    private static final LocalDate WINDOW_FROM = LocalDate.of(2026, 10, 5);

    private static final LocalDate WINDOW_TO = LocalDate.of(2026, 10, 18);

    private static final LocalDate DAY = LocalDate.of(2026, 10, 7);

    // ------------------------------------------------------------------
    // student-service stub (author display name lookup)
    // ------------------------------------------------------------------

    private static final String PROFILE_STUB_PREFIX = "/internal/students/";

    private static final Map<UUID, String> PROFILE_NAMES = new ConcurrentHashMap<>();

    private static final HttpServer PROFILE_STUB;

    private static final ExecutorService PROFILE_STUB_EXECUTOR;

    private static final int PROFILE_STUB_PORT;

    static {
        try {
            PROFILE_STUB = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            PROFILE_STUB_EXECUTOR = Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(runnable, "calendar-profile-stub");
                thread.setDaemon(true);
                return thread;
            });
            PROFILE_STUB.setExecutor(PROFILE_STUB_EXECUTOR);
            PROFILE_STUB.createContext(PROFILE_STUB_PREFIX, CalendarIntegrationTest::handleProfile);
            PROFILE_STUB.start();
            PROFILE_STUB_PORT = PROFILE_STUB.getAddress().getPort();
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @DynamicPropertySource
    static void studentServiceUrl(DynamicPropertyRegistry registry) {
        registry.add("nstu.student-service.url", () -> "http://127.0.0.1:" + PROFILE_STUB_PORT);
    }

    @BeforeEach
    void resetProfileStub() {
        PROFILE_NAMES.clear();
    }

    @AfterAll
    static void stopProfileStub() {
        PROFILE_STUB.stop(0);
        PROFILE_STUB_EXECUTOR.shutdownNow();
    }

    // ------------------------------------------------------------------
    // Visibility
    // ------------------------------------------------------------------

    @Test
    void guestSeesEmptyCalendarWithoutError() throws Exception {
        // A personal entry exists, but a guest must never see it.
        UUID author = UUID.randomUUID();
        createEntry(tokenFor(author, RoleNames.STUDENT), "Личное", DAY, null, Audience.ME);

        MvcResult result = getCalendar(guestToken(), WINDOW_FROM, WINDOW_TO);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(objectMapper.readTree(body(result))).isEmpty();
    }

    @Test
    void anonymousCalendarRequestIsUnauthorized() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/calendar")
                        .param("from", WINDOW_FROM.toString())
                        .param("to", WINDOW_TO.toString()))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void studentSeesOwnGroupAndStaffEntriesButNotForeignPersonalOrStaff() throws Exception {
        UUID studentId = UUID.randomUUID();
        UUID otherStudentId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        String student = tokenFor(studentId, RoleNames.STUDENT);
        String otherStudent = tokenFor(otherStudentId, RoleNames.STUDENT);
        String staff = tokenFor(staffId, RoleNames.STAFF);

        UUID own = createEntry(student, "Своё", DAY, null, Audience.ME);
        createEntry(otherStudent, "Чужое личное", DAY, null, Audience.ME);
        UUID group = createEntry(staff, "Для группы", DAY, null, Audience.GROUP);
        createEntry(staff, "Для персонала", DAY, null, Audience.STAFF);

        List<String> visible = ids(getCalendar(student, WINDOW_FROM, WINDOW_TO));

        assertThat(visible).containsExactlyInAnyOrder(own.toString(), group.toString());
    }

    @Test
    void staffSeesStaffAndGroupEntriesButNotForeignPersonal() throws Exception {
        UUID studentId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        String student = tokenFor(studentId, RoleNames.STUDENT);
        String staff = tokenFor(staffId, RoleNames.STAFF);

        createEntry(student, "Личное студента", DAY, null, Audience.ME);
        UUID group = createEntry(student, "Для группы", DAY, null, Audience.GROUP);
        UUID staffEntry = createEntry(staff, "Для персонала", DAY, null, Audience.STAFF);

        List<String> visible = ids(getCalendar(staff, WINDOW_FROM, WINDOW_TO));

        assertThat(visible).containsExactlyInAnyOrder(group.toString(), staffEntry.toString());
    }

    @Test
    void windowFiltersOutEntriesOnOtherDays() throws Exception {
        UUID studentId = UUID.randomUUID();
        String student = tokenFor(studentId, RoleNames.STUDENT);
        UUID inside = insertEntry(studentId, "В окне", WINDOW_FROM);
        insertEntry(studentId, "До окна", WINDOW_FROM.minusDays(1));
        insertEntry(studentId, "После окна", WINDOW_TO.plusDays(1));

        List<String> visible = ids(getCalendar(student, WINDOW_FROM, WINDOW_TO));

        assertThat(visible).containsExactly(inside.toString());
    }

    @Test
    void entriesOfADayAreOrderedByTimeThenTitleWithTimelessLast() throws Exception {
        UUID studentId = UUID.randomUUID();
        String student = tokenFor(studentId, RoleNames.STUDENT);
        createEntry(student, "Вечер", DAY, LocalTime.of(18, 0), Audience.ME);
        createEntry(student, "Без времени Б", DAY, null, Audience.ME);
        createEntry(student, "Утро", DAY, LocalTime.of(9, 0), Audience.ME);
        createEntry(student, "Без времени А", DAY, null, Audience.ME);

        JsonNode entries = responseJson(getCalendar(student, WINDOW_FROM, WINDOW_TO));

        assertThat(titles(entries)).containsExactly("Утро", "Вечер", "Без времени А", "Без времени Б");
    }

    // ------------------------------------------------------------------
    // Author snapshot (change add-preferences-and-calendar-ui; design.md D5)
    // ------------------------------------------------------------------

    @Test
    void createSnapshotsAuthorDisplayNameFromProfile() throws Exception {
        UUID studentId = UUID.randomUUID();
        PROFILE_NAMES.put(studentId, "Иванов Иван");
        String student = tokenFor(studentId, RoleNames.STUDENT);

        MvcResult result = postCalendar(student, entryBody("С автором", DAY, null, "ME", WINDOW_FROM, WINDOW_TO));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        JsonNode body = responseJson(result);
        assertThat(body.get("authorAccountId").asText()).isEqualTo(studentId.toString());
        assertThat(body.get("authorDisplayName").asText()).isEqualTo("Иванов Иван");
        assertThat(body.get("mine").asBoolean()).isTrue();
    }

    @Test
    void createKeepsNullAuthorNameWhenProfileIsMissing() throws Exception {
        UUID studentId = UUID.randomUUID();
        // No stub entry for this account: the profile lookup answers 404.
        String student = tokenFor(studentId, RoleNames.STUDENT);

        MvcResult result = postCalendar(student, entryBody("Без имени", DAY, null, "ME", WINDOW_FROM, WINDOW_TO));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        JsonNode body = responseJson(result);
        assertThat(body.get("authorDisplayName").isNull()).isTrue();
        assertThat(body.get("authorAccountId").asText()).isEqualTo(studentId.toString());
        assertThat(body.get("mine").asBoolean()).isTrue();
    }

    @Test
    void authorDisplayNameIsReturnedToOtherViewers() throws Exception {
        UUID authorId = UUID.randomUUID();
        PROFILE_NAMES.put(authorId, "Петров Пётр");
        String author = tokenFor(authorId, RoleNames.STUDENT);
        createEntry(author, "Групповое", DAY, null, Audience.GROUP);

        String other = tokenFor(UUID.randomUUID(), RoleNames.STUDENT);
        JsonNode entries = responseJson(getCalendar(other, WINDOW_FROM, WINDOW_TO));

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).get("authorDisplayName").asText()).isEqualTo("Петров Пётр");
        assertThat(entries.get(0).get("mine").asBoolean()).isFalse();
    }

    // ------------------------------------------------------------------
    // Creation and audience
    // ------------------------------------------------------------------

    @Test
    void studentCreatesPersonalAndGroupEntries() throws Exception {
        String student = tokenFor(UUID.randomUUID(), RoleNames.STUDENT);

        MvcResult personal = postCalendar(student, entryBody("Личное", DAY, null, "ME", WINDOW_FROM, WINDOW_TO));
        MvcResult group = postCalendar(student, entryBody("Групповое", DAY, null, "GROUP", WINDOW_FROM, WINDOW_TO));

        assertThat(personal.getResponse().getStatus()).isEqualTo(201);
        assertThat(responseJson(personal).get("audience").asText()).isEqualTo("ME");
        assertThat(responseJson(personal).get("mine").asBoolean()).isTrue();
        assertThat(group.getResponse().getStatus()).isEqualTo(201);
        assertThat(responseJson(group).get("audience").asText()).isEqualTo("GROUP");
    }

    @Test
    void omittedAudienceDefaultsToMe() throws Exception {
        String student = tokenFor(UUID.randomUUID(), RoleNames.STUDENT);

        MvcResult result = postCalendar(student, entryBody("Без адресата", DAY, null, null, WINDOW_FROM, WINDOW_TO));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(responseJson(result).get("audience").asText()).isEqualTo("ME");
    }

    @Test
    void studentCannotCreateStaffAudience() throws Exception {
        String student = tokenFor(UUID.randomUUID(), RoleNames.STUDENT);

        MvcResult result = postCalendar(student, entryBody("Для персонала", DAY, null, "STAFF", WINDOW_FROM, WINDOW_TO));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(errorCode(result)).isEqualTo("invalid_audience");
    }

    @Test
    void staffCanCreateStaffAudience() throws Exception {
        String staff = tokenFor(UUID.randomUUID(), RoleNames.STAFF);

        MvcResult result = postCalendar(staff, entryBody("Персоналу", DAY, null, "STAFF", WINDOW_FROM, WINDOW_TO));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void guestCannotCreateEntry() throws Exception {
        MvcResult result = postCalendar(guestToken(),
                entryBody("Гостевое", DAY, null, "ME", WINDOW_FROM, WINDOW_TO));

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(errorCode(result)).isEqualTo("forbidden");
    }

    @Test
    void createRequiresTitle() throws Exception {
        String student = tokenFor(UUID.randomUUID(), RoleNames.STUDENT);

        MvcResult result = postCalendar(student, entryBody("   ", DAY, null, "ME", WINDOW_FROM, WINDOW_TO));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(errorCode(result)).isEqualTo("invalid_title");
    }

    @Test
    void createRequiresStartsOn() throws Exception {
        String student = tokenFor(UUID.randomUUID(), RoleNames.STUDENT);

        MvcResult result = postCalendar(student, entryBody("Без даты", null, null, "ME", WINDOW_FROM, WINDOW_TO));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(errorCode(result)).isEqualTo("invalid_date");
    }

    @Test
    void createRejectsDateOutsideDisplayedWindow() throws Exception {
        String student = tokenFor(UUID.randomUUID(), RoleNames.STUDENT);

        MvcResult result = postCalendar(student,
                entryBody("Вне окна", WINDOW_TO.plusDays(1), null, "ME", WINDOW_FROM, WINDOW_TO));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(errorCode(result)).isEqualTo("invalid_date");
    }

    @Test
    void createAcceptsDateInsideDisplayedWindow() throws Exception {
        String student = tokenFor(UUID.randomUUID(), RoleNames.STUDENT);

        MvcResult result = postCalendar(student,
                entryBody("В окне", DAY, LocalTime.of(12, 30), "ME", WINDOW_FROM, WINDOW_TO));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(responseJson(result).get("startsOn").asText()).isEqualTo(DAY.toString());
        assertThat(responseJson(result).get("startsAt").asText()).startsWith("12:30");
    }

    // ------------------------------------------------------------------
    // Description limit (change add-preferences-and-calendar-ui; group 5)
    // ------------------------------------------------------------------

    @Test
    void createAcceptsDescriptionOfExactlyMaxLength() throws Exception {
        String student = tokenFor(UUID.randomUUID(), RoleNames.STUDENT);
        String description = "d".repeat(150);

        MvcResult result = postCalendarWithDescription(student, "С описанием", description);

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(responseJson(result).get("description").asText()).isEqualTo(description);
    }

    @Test
    void createRejectsDescriptionLongerThanMaxLength() throws Exception {
        String student = tokenFor(UUID.randomUUID(), RoleNames.STUDENT);

        MvcResult result = postCalendarWithDescription(student, "Длинное описание", "d".repeat(151));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(errorCode(result)).isEqualTo("invalid_description");
    }

    @Test
    void updateRejectsDescriptionLongerThanMaxLengthAndKeepsStoredValue() throws Exception {
        String author = tokenFor(UUID.randomUUID(), RoleNames.STUDENT);
        UUID id = createEntry(author, "Старое", DAY, null, Audience.ME);
        patchCalendar(id, author, updateBody(Map.of("description", "Сохранённое"), WINDOW_FROM, WINDOW_TO));

        MvcResult result = patchCalendar(id, author,
                updateBody(Map.of("description", "d".repeat(151)), WINDOW_FROM, WINDOW_TO));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(errorCode(result)).isEqualTo("invalid_description");
        assertThat(rowDescription(id)).isEqualTo("Сохранённое");
    }

    @Test
    void updateWithoutDescriptionKeepsStoredValue() throws Exception {
        String author = tokenFor(UUID.randomUUID(), RoleNames.STUDENT);
        UUID id = createEntry(author, "Старое", DAY, null, Audience.ME);
        patchCalendar(id, author, updateBody(Map.of("description", "Сохранённое"), WINDOW_FROM, WINDOW_TO));

        MvcResult result = patchCalendar(id, author,
                updateBody(Map.of("title", "Новое"), WINDOW_FROM, WINDOW_TO));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(responseJson(result).get("description").asText()).isEqualTo("Сохранённое");
        assertThat(rowDescription(id)).isEqualTo("Сохранённое");
    }

    // ------------------------------------------------------------------
    // Editing (change add-preferences-and-calendar-ui; design.md D6)
    // ------------------------------------------------------------------

    @Test
    void authorUpdatesOwnEntryAndGridReflectsChange() throws Exception {
        String author = tokenFor(UUID.randomUUID(), RoleNames.STUDENT);
        UUID id = createEntry(author, "Старое", DAY, null, Audience.ME);

        MvcResult result = patchCalendar(id, author,
                updateBody(Map.of("title", "Новое", "startsAt", "15:00"), WINDOW_FROM, WINDOW_TO));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = responseJson(result);
        assertThat(body.get("title").asText()).isEqualTo("Новое");
        assertThat(body.get("startsAt").asText()).startsWith("15:00");
        // The omitted fields keep their previous values.
        assertThat(body.get("startsOn").asText()).isEqualTo(DAY.toString());
        assertThat(body.get("audience").asText()).isEqualTo("ME");
        assertThat(body.get("mine").asBoolean()).isTrue();

        JsonNode entries = responseJson(getCalendar(author, WINDOW_FROM, WINDOW_TO));
        assertThat(titles(entries)).containsExactly("Новое");
    }

    @Test
    void adminUpdatesAnyEntry() throws Exception {
        String author = tokenFor(UUID.randomUUID(), RoleNames.STUDENT);
        UUID id = createEntry(author, "Студенческое", DAY, null, Audience.ME);

        MvcResult result = patchCalendar(id, adminToken(),
                updateBody(Map.of("title", "Админ изменил"), WINDOW_FROM, WINDOW_TO));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = responseJson(result);
        assertThat(body.get("title").asText()).isEqualTo("Админ изменил");
        assertThat(body.get("mine").asBoolean()).isFalse();
    }

    @Test
    void foreignUserCannotUpdateEntry() throws Exception {
        String author = tokenFor(UUID.randomUUID(), RoleNames.STUDENT);
        String other = tokenFor(UUID.randomUUID(), RoleNames.STUDENT);
        UUID id = createEntry(author, "Чужое", DAY, null, Audience.GROUP);

        MvcResult result = patchCalendar(id, other,
                updateBody(Map.of("title", "Взлом"), WINDOW_FROM, WINDOW_TO));

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(errorCode(result)).isEqualTo("calendar_forbidden");
        assertThat(rowTitle(id)).isEqualTo("Чужое");
    }

    @Test
    void updateUnknownEntryIsNotFound() throws Exception {
        MvcResult result = patchCalendar(UUID.randomUUID(), adminToken(),
                updateBody(Map.of("title", "Нет такого"), WINDOW_FROM, WINDOW_TO));

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(errorCode(result)).isEqualTo("calendar_not_found");
    }

    @Test
    void updateRejectsBlankTitle() throws Exception {
        String author = tokenFor(UUID.randomUUID(), RoleNames.STUDENT);
        UUID id = createEntry(author, "Старое", DAY, null, Audience.ME);

        MvcResult result = patchCalendar(id, author,
                updateBody(Map.of("title", "   "), WINDOW_FROM, WINDOW_TO));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(errorCode(result)).isEqualTo("invalid_title");
        assertThat(rowTitle(id)).isEqualTo("Старое");
    }

    @Test
    void updateRejectsDateOutsideDisplayedWindow() throws Exception {
        String author = tokenFor(UUID.randomUUID(), RoleNames.STUDENT);
        UUID id = createEntry(author, "Старое", DAY, null, Audience.ME);

        MvcResult result = patchCalendar(id, author,
                updateBody(Map.of("startsOn", WINDOW_TO.plusDays(1).toString()), WINDOW_FROM, WINDOW_TO));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(errorCode(result)).isEqualTo("invalid_date");
        assertThat(rowStartsOn(id)).isEqualTo(DAY);
    }

    @Test
    void studentCannotUpdateToStaffAudience() throws Exception {
        String author = tokenFor(UUID.randomUUID(), RoleNames.STUDENT);
        UUID id = createEntry(author, "Личное", DAY, null, Audience.ME);

        MvcResult result = patchCalendar(id, author,
                updateBody(Map.of("audience", "STAFF"), WINDOW_FROM, WINDOW_TO));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(errorCode(result)).isEqualTo("invalid_audience");
        assertThat(rowAudience(id)).isEqualTo("ME");
    }

    @Test
    void updateRefreshesUpdatedAt() throws Exception {
        String author = tokenFor(UUID.randomUUID(), RoleNames.STUDENT);
        UUID id = createEntry(author, "Старое", DAY, null, Audience.ME);
        // Move the stored timestamp a day back, so the assertion cannot flake on
        // two now() calls landing on the same clock tick.
        jdbcTemplate.update(
                "update event.calendar_entry set updated_at = now() - interval '1 day' where id = ?", id);
        Instant before = rowUpdatedAt(id);

        patchCalendar(id, author, updateBody(Map.of("title", "Новое"), WINDOW_FROM, WINDOW_TO));

        assertThat(rowUpdatedAt(id)).isAfter(before);
    }

    // ------------------------------------------------------------------
    // Window bounds
    // ------------------------------------------------------------------

    @Test
    void windowRequiresBothEndpoints() throws Exception {
        String student = tokenFor(UUID.randomUUID(), RoleNames.STUDENT);

        MvcResult result = mockMvc.perform(authorized(get("/api/calendar")
                        .param("from", WINDOW_FROM.toString()), student))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(errorCode(result)).isEqualTo("invalid_date");
    }

    @Test
    void windowRejectsEndBeforeStart() throws Exception {
        String student = tokenFor(UUID.randomUUID(), RoleNames.STUDENT);

        MvcResult result = getCalendar(student, WINDOW_TO, WINDOW_FROM);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(errorCode(result)).isEqualTo("invalid_date");
    }

    @Test
    void windowRejectsMoreThanThirtyOneDays() throws Exception {
        String student = tokenFor(UUID.randomUUID(), RoleNames.STUDENT);

        MvcResult result = getCalendar(student, WINDOW_FROM, WINDOW_FROM.plusDays(31));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(errorCode(result)).isEqualTo("invalid_date");
    }

    @Test
    void windowAcceptsThirtyOneDays() throws Exception {
        String student = tokenFor(UUID.randomUUID(), RoleNames.STUDENT);

        MvcResult result = getCalendar(student, WINDOW_FROM, WINDOW_FROM.plusDays(30));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void windowRejectsMalformedDate() throws Exception {
        String student = tokenFor(UUID.randomUUID(), RoleNames.STUDENT);

        MvcResult result = getCalendar(student, "not-a-date", WINDOW_TO);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(errorCode(result)).isEqualTo("invalid_date");
    }

    // ------------------------------------------------------------------
    // Deletion
    // ------------------------------------------------------------------

    @Test
    void authorDeletesOwnEntry() throws Exception {
        UUID studentId = UUID.randomUUID();
        String student = tokenFor(studentId, RoleNames.STUDENT);
        UUID id = createEntry(student, "Своё", DAY, null, Audience.ME);

        MvcResult result = deleteCalendar(id, student);

        assertThat(result.getResponse().getStatus()).isEqualTo(204);
        assertThat(calendarRowCount(id)).isZero();
    }

    @Test
    void staffCannotDeleteForeignEntry() throws Exception {
        String student = tokenFor(UUID.randomUUID(), RoleNames.STUDENT);
        String staff = tokenFor(UUID.randomUUID(), RoleNames.STAFF);
        UUID id = createEntry(student, "Чужое", DAY, null, Audience.GROUP);

        MvcResult result = deleteCalendar(id, staff);

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(errorCode(result)).isEqualTo("calendar_forbidden");
        assertThat(calendarRowCount(id)).isEqualTo(1);
    }

    @Test
    void studentCannotDeleteForeignEntry() throws Exception {
        String author = tokenFor(UUID.randomUUID(), RoleNames.STUDENT);
        String other = tokenFor(UUID.randomUUID(), RoleNames.STUDENT);
        UUID id = createEntry(author, "Чужое", DAY, null, Audience.GROUP);

        MvcResult result = deleteCalendar(id, other);

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(calendarRowCount(id)).isEqualTo(1);
    }

    @Test
    void adminDeletesAnyEntry() throws Exception {
        String student = tokenFor(UUID.randomUUID(), RoleNames.STUDENT);
        String admin = tokenFor(UUID.randomUUID(), RoleNames.ADMIN);
        UUID id = createEntry(student, "Студенческое", DAY, null, Audience.ME);

        MvcResult result = deleteCalendar(id, admin);

        assertThat(result.getResponse().getStatus()).isEqualTo(204);
        assertThat(calendarRowCount(id)).isZero();
    }

    @Test
    void deleteUnknownEntryIsNotFound() throws Exception {
        String student = tokenFor(UUID.randomUUID(), RoleNames.STUDENT);

        MvcResult result = deleteCalendar(UUID.randomUUID(), student);

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(errorCode(result)).isEqualTo("calendar_not_found");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private MvcResult getCalendar(String token, Object from, Object to) throws Exception {
        return mockMvc.perform(authorized(get("/api/calendar")
                        .param("from", String.valueOf(from))
                        .param("to", String.valueOf(to)), token))
                .andReturn();
    }

    private MvcResult postCalendar(String token, Map<String, Object> body) throws Exception {
        return mockMvc.perform(authorized(post("/api/calendar"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andReturn();
    }

    private MvcResult postCalendarWithDescription(String token, String title, String description) throws Exception {
        Map<String, Object> body = entryBody(title, DAY, null, "ME", WINDOW_FROM, WINDOW_TO);
        body.put("description", description);
        return postCalendar(token, body);
    }

    private MvcResult patchCalendar(UUID id, String token, Map<String, Object> body) throws Exception {
        return mockMvc.perform(authorized(patch("/api/calendar/{id}", id), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andReturn();
    }

    private MvcResult deleteCalendar(UUID id, String token) throws Exception {
        return mockMvc.perform(authorized(delete("/api/calendar/{id}", id), token)).andReturn();
    }

    private Map<String, Object> entryBody(String title,
                                          LocalDate startsOn,
                                          LocalTime startsAt,
                                          String audience,
                                          LocalDate from,
                                          LocalDate to) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        if (startsOn != null) {
            body.put("startsOn", startsOn.toString());
        }
        if (startsAt != null) {
            body.put("startsAt", startsAt.toString());
        }
        if (audience != null) {
            body.put("audience", audience);
        }
        if (from != null) {
            body.put("from", from.toString());
        }
        if (to != null) {
            body.put("to", to.toString());
        }
        return body;
    }

    private Map<String, Object> updateBody(Map<String, Object> fields, LocalDate from, LocalDate to) {
        Map<String, Object> body = new HashMap<>(fields);
        if (from != null) {
            body.put("from", from.toString());
        }
        if (to != null) {
            body.put("to", to.toString());
        }
        return body;
    }

    private UUID createEntry(String token,
                             String title,
                             LocalDate startsOn,
                             LocalTime startsAt,
                             Audience audience) throws Exception {
        MvcResult result = postCalendar(token,
                entryBody(title, startsOn, startsAt, audience.name(), WINDOW_FROM, WINDOW_TO));
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return UUID.fromString(responseJson(result).get("id").asText());
    }

    private List<String> ids(MvcResult result) throws Exception {
        return responseJson(result).findValuesAsText("id");
    }

    /**
     * Inserts an entry directly, bypassing the API. Used for dates outside the
     * displayed window, which {@code POST /api/calendar} deliberately rejects.
     */
    private UUID insertEntry(UUID author, String title, LocalDate startsOn) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into event.calendar_entry "
                        + "(id, group_id, author_account_id, title, starts_on, audience) "
                        + "values (?, ?, ?, ?, ?, 'ME')",
                id, Groups.DEFAULT_GROUP_ID, author, title, startsOn);
        return id;
    }

    private List<String> titles(JsonNode entries) {
        return entries.findValuesAsText("title");
    }

    private int calendarRowCount(UUID id) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from event.calendar_entry where id = ?", Integer.class, id);
        return count == null ? 0 : count;
    }

    private String rowTitle(UUID id) {
        return jdbcTemplate.queryForObject(
                "select title from event.calendar_entry where id = ?", String.class, id);
    }

    private LocalDate rowStartsOn(UUID id) {
        return jdbcTemplate.queryForObject(
                "select starts_on from event.calendar_entry where id = ?", LocalDate.class, id);
    }

    private String rowDescription(UUID id) {
        return jdbcTemplate.queryForObject(
                "select description from event.calendar_entry where id = ?", String.class, id);
    }

    private String rowAudience(UUID id) {
        return jdbcTemplate.queryForObject(
                "select audience from event.calendar_entry where id = ?", String.class, id);
    }

    private Instant rowUpdatedAt(UUID id) {
        java.sql.Timestamp value = jdbcTemplate.queryForObject(
                "select updated_at from event.calendar_entry where id = ?", java.sql.Timestamp.class, id);
        return value == null ? null : value.toInstant();
    }

    private static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static void handleProfile(HttpExchange exchange) throws IOException {
        try (InputStream requestBody = exchange.getRequestBody()) {
            requestBody.readAllBytes();
        }
        UUID accountId = parseAccountId(exchange.getRequestURI().getPath());
        String name = accountId == null ? null : PROFILE_NAMES.get(accountId);
        if (name == null) {
            byte[] payload = "{\"error\":\"not_found\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(404, payload.length);
            try (OutputStream responseBody = exchange.getResponseBody()) {
                responseBody.write(payload);
            }
            return;
        }
        byte[] payload = ("{\"accountId\":\"" + accountId + "\",\"fullName\":\"" + name
                + "\",\"groupId\":\"" + Groups.DEFAULT_GROUP_ID + "\"}")
                .getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, payload.length);
        try (OutputStream responseBody = exchange.getResponseBody()) {
            responseBody.write(payload);
        }
    }

    private static UUID parseAccountId(String path) {
        if (path == null || !path.startsWith(PROFILE_STUB_PREFIX)) {
            return null;
        }
        try {
            return UUID.fromString(path.substring(PROFILE_STUB_PREFIX.length()));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
