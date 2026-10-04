package ru.nstu.system.student.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.nstu.system.security.ParsedToken;
import ru.nstu.system.security.RoleNames;
import ru.nstu.system.security.SecurityContextSupport;
import ru.nstu.system.student.error.ApiException;
import ru.nstu.system.student.service.NoteService;
import ru.nstu.system.student.service.NoteService.AttachmentDownload;
import ru.nstu.system.student.web.dto.CreateNoteRequest;
import ru.nstu.system.student.web.dto.NoteAttachmentResponse;
import ru.nstu.system.student.web.dto.NoteListResponse;
import ru.nstu.system.student.web.dto.NoteResponse;
import ru.nstu.system.student.web.dto.UpdateNoteRequest;

/**
 * Personal notes API, mounted under {@code /api/notes} (change add-notes-module,
 * design.md D4).
 *
 * <p>Every endpoint requires a valid access token (401 without one, enforced by
 * the security filter chain). A guest session is authenticated but owns no
 * account, so it receives {@code 403}. Ownership itself is verified in
 * {@link NoteService}: a foreign or unknown note answers {@code 404}, never
 * {@code 403}, so note existence is not revealed.</p>
 */
@RestController
@RequestMapping("/api/notes")
public class NoteController {

    private final NoteService noteService;

    public NoteController(NoteService noteService) {
        this.noteService = noteService;
    }

    /** Lists the caller's notes together with the storage quota. */
    @GetMapping
    public NoteListResponse list() {
        return noteService.listNotes(currentAccountId());
    }

    /** Creates a note; a blank or too long title yields {@code invalid_note}. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NoteResponse create(@RequestBody(required = false) CreateNoteRequest request) {
        String title = request == null ? null : request.title();
        String body = request == null ? null : request.body();
        return noteService.createNote(currentAccountId(), title, body);
    }

    /** Partially updates a note (title and/or body). */
    @PatchMapping("/{id}")
    public NoteResponse update(@PathVariable UUID id,
                               @RequestBody(required = false) UpdateNoteRequest request) {
        String title = request == null ? null : request.title();
        String body = request == null ? null : request.body();
        return noteService.updateNote(currentAccountId(), id, title, body);
    }

    /** Deletes a note and all of its attachments. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        noteService.deleteNote(currentAccountId(), id);
    }

    /** Uploads a multipart attachment (field name {@code file}). */
    @PostMapping("/{id}/attachments")
    @ResponseStatus(HttpStatus.CREATED)
    public NoteAttachmentResponse upload(@PathVariable UUID id,
                                         @RequestPart(name = "file", required = false) MultipartFile file) {
        if (file == null) {
            throw ApiException.badRequest("invalid_attachment", "Файл не передан");
        }
        byte[] content = readBytes(file);
        return NoteAttachmentResponse.from(noteService.uploadAttachment(
                currentAccountId(), id, file.getOriginalFilename(), file.getContentType(), content));
    }

    /** Downloads an owned attachment as a file, hardened against in-browser execution. */
    @GetMapping("/{id}/attachments/{attachmentId}")
    public ResponseEntity<byte[]> download(@PathVariable UUID id, @PathVariable UUID attachmentId) {
        AttachmentDownload download = noteService.downloadAttachment(currentAccountId(), id, attachmentId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(download.fileName(), StandardCharsets.UTF_8)
                .build());
        headers.set("X-Content-Type-Options", "nosniff");
        return new ResponseEntity<>(download.content(), headers, HttpStatus.OK);
    }

    /** Deletes an owned attachment. */
    @DeleteMapping("/{id}/attachments/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAttachment(@PathVariable UUID id, @PathVariable UUID attachmentId) {
        noteService.deleteAttachment(currentAccountId(), id, attachmentId);
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw ApiException.badRequest("invalid_attachment", "Не удалось прочитать файл");
        }
    }

    /**
     * Resolves the caller's account id. A guest is rejected with 403 before the
     * subject is parsed: a guest subject ({@code guest:<uuid>}) is not an account.
     */
    private static UUID currentAccountId() {
        ParsedToken token = SecurityContextSupport.currentToken()
                .orElseThrow(() -> ApiException.unauthorized("unauthorized", "Требуется аутентификация"));
        if (token.roles().contains(RoleNames.GUEST)) {
            throw ApiException.forbidden("guest_has_no_notes", "Модуль заметок недоступен гостю");
        }
        try {
            return UUID.fromString(token.subject());
        } catch (IllegalArgumentException exception) {
            throw ApiException.unauthorized("unauthorized", "Требуется аутентификация");
        }
    }
}
