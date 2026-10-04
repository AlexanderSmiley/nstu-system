package ru.nstu.system.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ru.nstu.system.student.error.ApiException;
import ru.nstu.system.student.service.NoteService;

/**
 * Integration tests of the personal notes module (change add-notes-module,
 * design.md D1-D7): ownership/privacy, CRUD, attachment limits and download
 * hardening, the 10 MiB quota, concurrent uploads and guest exclusion.
 *
 * <p>Limits are overridden to 5 MiB total and 4 MiB per file in
 * {@link AbstractStudentIntegrationTest} so the boundary tests stay fast.</p>
 */
class NoteIntegrationTest extends AbstractStudentIntegrationTest {

    private static final long QUOTA = 5L * 1024 * 1024;

    private static final int MAX_FILE = 4 * 1024 * 1024;

    private static final byte[] SMALL = "hello notes".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private NoteService noteService;

    // ------------------------------------------------------------------
    // Privacy
    // ------------------------------------------------------------------

    @Test
    void listReturnsOnlyOwnNotesWithAttachmentsAndQuota() throws Exception {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        UUID aliceNote = createNote(alice, "Моя заметка");
        attach(alice, aliceNote, "a.txt", "text/plain", SMALL);
        createNote(bob, "Чужая заметка");

        mockMvc.perform(authorized(get("/api/notes"), studentToken(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes.length()").value(1))
                .andExpect(jsonPath("$.notes[0].id").value(aliceNote.toString()))
                .andExpect(jsonPath("$.notes[0].title").value("Моя заметка"))
                .andExpect(jsonPath("$.notes[0].attachments.length()").value(1))
                .andExpect(jsonPath("$.notes[0].attachments[0].fileName").value("a.txt"))
                .andExpect(jsonPath("$.quota.usedBytes").value(SMALL.length))
                .andExpect(jsonPath("$.quota.limitBytes").value(QUOTA));
    }

    @Test
    void foreignNoteAndAttachmentAreNotFound() throws Exception {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        UUID aliceNote = createNote(alice, "Секрет");
        UUID attachment = attach(alice, aliceNote, "s.txt", "text/plain", SMALL);

        mockMvc.perform(authorized(patch("/api/notes/{id}", aliceNote), studentToken(bob))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "hacked"))))
                .andExpect(status().isNotFound());
        mockMvc.perform(authorized(delete("/api/notes/{id}", aliceNote), studentToken(bob)))
                .andExpect(status().isNotFound());
        mockMvc.perform(authorized(
                        get("/api/notes/{id}/attachments/{aid}", aliceNote, attachment), studentToken(bob)))
                .andExpect(status().isNotFound());
        mockMvc.perform(authorized(
                        delete("/api/notes/{id}/attachments/{aid}", aliceNote, attachment), studentToken(bob)))
                .andExpect(status().isNotFound());

        // The owner still sees the note and its attachment untouched.
        assertThat(noteService.listNotes(alice).notes()).hasSize(1);
        assertThat(noteService.listNotes(alice).quota().usedBytes()).isEqualTo(SMALL.length);
    }

    @Test
    void administratorCannotSeeForeignNotes() throws Exception {
        UUID alice = UUID.randomUUID();
        UUID admin = UUID.randomUUID();
        UUID aliceNote = createNote(alice, "Секрет");

        mockMvc.perform(authorized(get("/api/notes"), adminToken(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes.length()").value(0));
        mockMvc.perform(authorized(patch("/api/notes/{id}", aliceNote), adminToken(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "x"))))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------
    // Access control
    // ------------------------------------------------------------------

    @Test
    void guestIsForbiddenOnEveryNotesEndpoint() throws Exception {
        UUID noteId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        String guest = guestToken();

        List<MockHttpServletRequestBuilder> requests = List.of(
                get("/api/notes"),
                post("/api/notes").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "x"))),
                patch("/api/notes/{id}", noteId).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "x"))),
                delete("/api/notes/{id}", noteId),
                multipart("/api/notes/{id}/attachments", noteId)
                        .file(new MockMultipartFile("file", "a.txt", "text/plain", SMALL)),
                get("/api/notes/{id}/attachments/{aid}", noteId, attachmentId),
                delete("/api/notes/{id}/attachments/{aid}", noteId, attachmentId));

        for (MockHttpServletRequestBuilder request : requests) {
            mockMvc.perform(authorized(request, guest))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void requestsWithoutTokenAreUnauthorized() throws Exception {
        mockMvc.perform(get("/api/notes")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/notes").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "x"))))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    // CRUD
    // ------------------------------------------------------------------

    @Test
    void createsTrimsTitleAndStoresPlainTextBody() throws Exception {
        UUID account = UUID.randomUUID();

        mockMvc.perform(authorized(post("/api/notes"), studentToken(account))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "  Список  ", "body", "<b>не разметка</b>"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Список"))
                .andExpect(jsonPath("$.body").value("<b>не разметка</b>"))
                .andExpect(jsonPath("$.attachments.length()").value(0));

        mockMvc.perform(authorized(get("/api/notes"), studentToken(account)))
                .andExpect(jsonPath("$.notes.length()").value(1));
    }

    @Test
    void createRejectsBlankAndTooLongTitle() throws Exception {
        UUID account = UUID.randomUUID();

        mockMvc.perform(authorized(post("/api/notes"), studentToken(account))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "   "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_note"));

        mockMvc.perform(authorized(post("/api/notes"), studentToken(account))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "x".repeat(201)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_note"));

        assertThat(noteCount()).isZero();
    }

    @Test
    void updateChangesTitleAndBodyAndRejectsBlankTitle() throws Exception {
        UUID account = UUID.randomUUID();
        UUID note = createNote(account, "Старое");

        mockMvc.perform(authorized(patch("/api/notes/{id}", note), studentToken(account))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "Новое", "body", "текст"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Новое"))
                .andExpect(jsonPath("$.body").value("текст"));

        mockMvc.perform(authorized(patch("/api/notes/{id}", note), studentToken(account))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "  "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_note"));
    }

    @Test
    void deletingNoteRemovesAttachmentsAndFreesQuota() throws Exception {
        UUID account = UUID.randomUUID();
        UUID note = createNote(account, "С файлом");
        attach(account, note, "f.txt", "text/plain", SMALL);

        assertThat(noteService.listNotes(account).quota().usedBytes()).isEqualTo(SMALL.length);

        mockMvc.perform(authorized(delete("/api/notes/{id}", note), studentToken(account)))
                .andExpect(status().isNoContent());

        assertThat(noteService.listNotes(account).quota().usedBytes()).isZero();
        assertThat(attachmentCount()).isZero();
        assertThat(noteCount()).isZero();
    }

    // ------------------------------------------------------------------
    // Attachments
    // ------------------------------------------------------------------

    @Test
    void uploadSanitizesFileNameAndDownloadSendsHardenedHeaders() throws Exception {
        UUID account = UUID.randomUUID();
        UUID note = createNote(account, "Заметка");
        byte[] content = "payload".getBytes(StandardCharsets.UTF_8);

        MvcResult upload = upload(note, studentToken(account), "C:\\temp\\отчёт.pdf", "text/html", content);
        assertThat(upload.getResponse().getStatus()).isEqualTo(201);
        JsonNode body = objectMapper.readTree(upload.getResponse().getContentAsString());
        assertThat(body.get("fileName").asText()).isEqualTo("отчёт.pdf");
        String attachmentId = body.get("id").asText();

        MvcResult download = mockMvc.perform(authorized(
                        get("/api/notes/{id}/attachments/{aid}", note, attachmentId), studentToken(account)))
                .andReturn();

        assertThat(download.getResponse().getStatus()).isEqualTo(200);
        assertThat(download.getResponse().getContentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        assertThat(download.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(download.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .startsWith("attachment;")
                .contains("filename*=UTF-8''");
        assertThat(download.getResponse().getContentAsByteArray()).isEqualTo(content);
    }

    @Test
    void rejectsEmptyFileAndUnusableName() throws Exception {
        UUID account = UUID.randomUUID();
        UUID note = createNote(account, "Заметка");

        MvcResult empty = upload(note, studentToken(account), "empty.txt", "text/plain", new byte[0]);
        assertThat(empty.getResponse().getStatus()).isEqualTo(400);
        assertThat(errorCode(empty)).isEqualTo("invalid_attachment");

        MvcResult blankName = upload(note, studentToken(account), "   ", "text/plain", SMALL);
        assertThat(blankName.getResponse().getStatus()).isEqualTo(400);
        assertThat(errorCode(blankName)).isEqualTo("invalid_attachment");

        assertThat(attachmentCount()).isZero();
    }

    @Test
    void rejectsAttachmentLargerThanTheFileLimit() throws Exception {
        UUID account = UUID.randomUUID();
        UUID note = createNote(account, "Заметка");

        MvcResult rejected = upload(note, studentToken(account), "big.bin", null, new byte[MAX_FILE + 1]);

        assertThat(rejected.getResponse().getStatus()).isEqualTo(413);
        assertThat(errorCode(rejected)).isEqualTo("attachment_too_large");
        assertThat(attachmentCount()).isZero();
    }

    // ------------------------------------------------------------------
    // Quota
    // ------------------------------------------------------------------

    @Test
    void enforcesQuotaBoundaryAndRejectsMore() throws Exception {
        UUID account = UUID.randomUUID();
        UUID note = createNote(account, "Заметка");

        assertThat(upload(note, studentToken(account), "a.bin", null, new byte[MAX_FILE])
                .getResponse().getStatus()).isEqualTo(201);
        assertThat(upload(note, studentToken(account), "b.bin", null, new byte[1024 * 1024])
                .getResponse().getStatus()).isEqualTo(201);

        MvcResult rejected = upload(note, studentToken(account), "c.bin", null, new byte[] {1});
        assertThat(rejected.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(rejected)).isEqualTo("note_quota_exceeded");
        assertThat(noteService.listNotes(account).quota().usedBytes()).isEqualTo(QUOTA);
    }

    @Test
    void deletingAttachmentFreesQuota() throws Exception {
        UUID account = UUID.randomUUID();
        UUID note = createNote(account, "Заметка");
        UUID first = attach(account, note, "a.bin", null, new byte[MAX_FILE]);
        attach(account, note, "b.bin", null, new byte[1024 * 1024]);
        assertThat(noteService.listNotes(account).quota().usedBytes()).isEqualTo(QUOTA);

        mockMvc.perform(authorized(
                        delete("/api/notes/{id}/attachments/{aid}", note, first), studentToken(account)))
                .andExpect(status().isNoContent());

        assertThat(noteService.listNotes(account).quota().usedBytes()).isEqualTo(1024L * 1024);
        assertThat(upload(note, studentToken(account), "c.bin", null, new byte[MAX_FILE])
                .getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void concurrentUploadsDoNotExceedQuota() throws Exception {
        UUID account = UUID.randomUUID();
        UUID noteA = createNote(account, "A");
        UUID noteB = createNote(account, "B");

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<String> a = () -> attemptUpload(account, noteA, start);
            Callable<String> b = () -> attemptUpload(account, noteB, start);
            Future<String> first = pool.submit(a);
            Future<String> second = pool.submit(b);
            start.countDown();

            List<String> results = List.of(
                    first.get(30, TimeUnit.SECONDS),
                    second.get(30, TimeUnit.SECONDS));

            assertThat(results).containsExactlyInAnyOrder("ok", "note_quota_exceeded");
            assertThat(noteService.listNotes(account).quota().usedBytes()).isEqualTo(MAX_FILE);
        } finally {
            pool.shutdownNow();
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private UUID createNote(UUID accountId, String title) {
        return noteService.createNote(accountId, title, "body").id();
    }

    private UUID attach(UUID accountId, UUID noteId, String fileName, String contentType, byte[] content) {
        return noteService.uploadAttachment(accountId, noteId, fileName, contentType, content).id();
    }

    private String attemptUpload(UUID accountId, UUID noteId, CountDownLatch start) {
        try {
            start.await();
            noteService.uploadAttachment(accountId, noteId, "race.bin", "application/octet-stream",
                    new byte[MAX_FILE]);
            return "ok";
        } catch (ApiException exception) {
            return exception.getCode();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return "interrupted";
        }
    }

    private MvcResult upload(UUID noteId, String token, String fileName, String contentType, byte[] content)
            throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", fileName, contentType, content);
        return mockMvc.perform(authorized(
                        multipart("/api/notes/{id}/attachments", noteId).file(file), token))
                .andReturn();
    }

    private static MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder builder, String token) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private String errorCode(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("error").asText();
    }

    private int noteCount() {
        Integer count = jdbcTemplate.queryForObject("select count(*) from student.note", Integer.class);
        return count == null ? 0 : count;
    }

    private int attachmentCount() {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from student.note_attachment", Integer.class);
        return count == null ? 0 : count;
    }
}
