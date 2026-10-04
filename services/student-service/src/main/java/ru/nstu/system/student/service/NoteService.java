package ru.nstu.system.student.service;

import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.nstu.system.student.config.NotesProperties;
import ru.nstu.system.student.domain.Note;
import ru.nstu.system.student.domain.NoteAttachment;
import ru.nstu.system.student.domain.NoteAttachmentRepository;
import ru.nstu.system.student.domain.NoteRepository;
import ru.nstu.system.student.error.ApiException;
import ru.nstu.system.student.web.dto.NoteListResponse;
import ru.nstu.system.student.web.dto.NoteQuotaResponse;
import ru.nstu.system.student.web.dto.NoteResponse;

/**
 * Personal notes: ownership checks, validation, attachment storage and the
 * per-account quota (change add-notes-module, design.md D1/D3/D5).
 *
 * <p>Privacy is enforced here and in the repositories: every query is scoped by
 * the caller's {@code account_id}, and an unknown <em>or foreign</em> note yields
 * {@code 404} so its existence is never confirmed. Administrators are no
 * exception.</p>
 *
 * <p>The quota check and the insert run in one transaction that starts with
 * {@code pg_advisory_xact_lock(hashtext(:accountId))} (design.md D3): concurrent
 * uploads of one account are serialised, so together they can never exceed the
 * limit. The advisory lock is scoped to the transaction and released on commit
 * or rollback.</p>
 */
@Service
public class NoteService {

    /** Maximum number of characters in a note title. */
    static final int TITLE_MAX_LENGTH = 200;

    /** Maximum number of characters persisted for a file name. */
    static final int FILE_NAME_MAX_LENGTH = 255;

    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    /** Serialises uploads of a single account; released at transaction end. */
    private static final String ADVISORY_LOCK_SQL = "select pg_advisory_xact_lock(hashtext(?))";

    private final NoteRepository noteRepository;

    private final NoteAttachmentRepository attachmentRepository;

    private final JdbcTemplate jdbcTemplate;

    private final NotesProperties properties;

    public NoteService(NoteRepository noteRepository,
                       NoteAttachmentRepository attachmentRepository,
                       JdbcTemplate jdbcTemplate,
                       NotesProperties properties) {
        this.noteRepository = Objects.requireNonNull(noteRepository, "noteRepository");
        this.attachmentRepository = Objects.requireNonNull(attachmentRepository, "attachmentRepository");
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    /** @return the caller's notes and their combined storage usage */
    @Transactional(readOnly = true)
    public NoteListResponse listNotes(UUID accountId) {
        Objects.requireNonNull(accountId, "accountId");
        List<Note> notes = noteRepository.findByAccount(accountId);
        List<NoteAttachment> attachments = attachmentRepository.findByAccount(accountId);
        Map<UUID, List<NoteAttachment>> byNote = attachments.stream()
                .collect(Collectors.groupingBy(NoteAttachment::noteId));
        List<NoteResponse> responses = notes.stream()
                .map(note -> NoteResponse.from(note, byNote.getOrDefault(note.id(), List.of())))
                .toList();
        long usedBytes = attachments.stream().mapToLong(NoteAttachment::sizeBytes).sum();
        return new NoteListResponse(responses, new NoteQuotaResponse(usedBytes, properties.getQuotaBytes()));
    }

    /**
     * Creates a note with a validated title.
     *
     * @throws ApiException 400 {@code invalid_note} for a blank or too long title
     */
    @Transactional
    public NoteResponse createNote(UUID accountId, String title, String body) {
        Objects.requireNonNull(accountId, "accountId");
        String normalizedTitle = validateTitle(title);
        Instant now = Instant.now();
        Note note = new Note(UUID.randomUUID(), accountId, normalizedTitle, normalizeBody(body), now, now);
        noteRepository.insert(note);
        return NoteResponse.from(note, List.of());
    }

    /**
     * Partially updates a note. A {@code null} field keeps its current value; an
     * empty body string clears the body.
     *
     * @throws ApiException 404 when the note is unknown or not owned,
     *                      400 {@code invalid_note} for a blank or too long title
     */
    @Transactional
    public NoteResponse updateNote(UUID accountId, UUID noteId, String title, String body) {
        Objects.requireNonNull(accountId, "accountId");
        Note note = requireNote(accountId, noteId);
        String newTitle = title == null ? note.title() : validateTitle(title);
        String newBody = body == null ? note.body() : normalizeBody(body);
        Instant now = Instant.now();
        noteRepository.update(noteId, accountId, newTitle, newBody, now);
        Note updated = new Note(note.id(), note.accountId(), newTitle, newBody, note.createdAt(), now);
        return NoteResponse.from(updated, attachmentRepository.findByNoteAndAccount(noteId, accountId));
    }

    /**
     * Deletes an owned note; its attachments are removed by the foreign key
     * cascade and their bytes free the quota.
     *
     * @throws ApiException 404 when the note is unknown or not owned
     */
    @Transactional
    public void deleteNote(UUID accountId, UUID noteId) {
        Objects.requireNonNull(accountId, "accountId");
        if (noteRepository.delete(noteId, accountId) == 0) {
            throw noteNotFound();
        }
    }

    /**
     * Stores an attachment after enforcing the per-file limit and the account
     * quota.
     *
     * @param originalFileName client-provided name, sanitised before storing
     * @param contentType      client-provided MIME type; stored as-is and never
     *                         trusted for serving
     * @param content          raw bytes
     * @throws ApiException 404 when the note is unknown or not owned,
     *                      400 {@code invalid_attachment} for an empty file or an
     *                      unusable name, 413 {@code attachment_too_large} when
     *                      the file exceeds the per-file limit, 409
     *                      {@code note_quota_exceeded} when it would exceed the quota
     */
    @Transactional
    public NoteAttachment uploadAttachment(UUID accountId,
                                           UUID noteId,
                                           String originalFileName,
                                           String contentType,
                                           byte[] content) {
        Objects.requireNonNull(accountId, "accountId");
        requireNote(accountId, noteId);
        if (content == null || content.length == 0) {
            throw invalidAttachment("Файл пуст");
        }
        String fileName = sanitizeFileName(originalFileName);
        if (fileName == null) {
            throw invalidAttachment("Недопустимое имя файла");
        }
        long maxAttachmentBytes = properties.getMaxAttachmentBytes();
        if (content.length > maxAttachmentBytes) {
            throw ApiException.payloadTooLarge("attachment_too_large",
                    "Размер файла не должен превышать " + toMegabytes(maxAttachmentBytes) + " МБ");
        }
        String resolvedContentType =
                contentType == null || contentType.isBlank() ? DEFAULT_CONTENT_TYPE : contentType;

        lockAccount(accountId);
        long usedBytes = attachmentRepository.sumSizeByAccount(accountId);
        long quotaBytes = properties.getQuotaBytes();
        if (usedBytes + content.length > quotaBytes) {
            throw ApiException.conflict("note_quota_exceeded",
                    "Превышена квота вложений: занято " + toMegabytes(usedBytes) + " из "
                            + toMegabytes(quotaBytes) + " МБ");
        }

        NoteAttachment attachment = new NoteAttachment(
                UUID.randomUUID(), noteId, fileName, resolvedContentType, content.length, Instant.now());
        attachmentRepository.insert(attachment, content);
        return attachment;
    }

    /**
     * Loads an owned attachment payload for download.
     *
     * @throws ApiException 404 when the note or attachment is unknown or not owned
     */
    @Transactional(readOnly = true)
    public AttachmentDownload downloadAttachment(UUID accountId, UUID noteId, UUID attachmentId) {
        Objects.requireNonNull(accountId, "accountId");
        requireNote(accountId, noteId);
        NoteAttachment attachment = requireAttachment(accountId, noteId, attachmentId);
        byte[] content = attachmentRepository.findContent(attachmentId, accountId)
                .orElseThrow(NoteService::attachmentNotFound);
        return new AttachmentDownload(attachment.fileName(), content);
    }

    /**
     * Deletes an owned attachment, freeing its bytes from the quota.
     *
     * @throws ApiException 404 when the note or attachment is unknown or not owned
     */
    @Transactional
    public void deleteAttachment(UUID accountId, UUID noteId, UUID attachmentId) {
        Objects.requireNonNull(accountId, "accountId");
        requireNote(accountId, noteId);
        NoteAttachment attachment = requireAttachment(accountId, noteId, attachmentId);
        attachmentRepository.delete(attachment.id(), accountId);
    }

    /**
     * Removes path separators, control characters and traversal names from a
     * client-provided file name (design.md D5). The client value is never trusted.
     *
     * @return a storable name, or {@code null} when nothing usable remains
     */
    static String sanitizeFileName(String rawFileName) {
        if (rawFileName == null) {
            return null;
        }
        String name = rawFileName;
        int separator = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (separator >= 0) {
            name = name.substring(separator + 1);
        }
        StringBuilder cleaned = new StringBuilder(name.length());
        for (int index = 0; index < name.length(); index++) {
            char character = name.charAt(index);
            if (character >= 0x20 && character != 0x7F) {
                cleaned.append(character);
            }
        }
        String result = cleaned.toString().trim();
        if (result.isEmpty() || ".".equals(result) || "..".equals(result)) {
            return null;
        }
        return result.length() > FILE_NAME_MAX_LENGTH
                ? result.substring(0, FILE_NAME_MAX_LENGTH)
                : result;
    }

    private static String validateTitle(String rawTitle) {
        String title = rawTitle == null ? "" : rawTitle.trim();
        if (title.isEmpty()) {
            throw ApiException.badRequest("invalid_note", "Название заметки не может быть пустым");
        }
        if (title.length() > TITLE_MAX_LENGTH) {
            throw ApiException.badRequest("invalid_note",
                    "Название заметки не должно превышать " + TITLE_MAX_LENGTH + " символов");
        }
        return title;
    }

    private static String normalizeBody(String rawBody) {
        if (rawBody == null || rawBody.isEmpty()) {
            return null;
        }
        return rawBody;
    }

    private Note requireNote(UUID accountId, UUID noteId) {
        return noteRepository.findByIdAndAccount(noteId, accountId).orElseThrow(NoteService::noteNotFound);
    }

    private NoteAttachment requireAttachment(UUID accountId, UUID noteId, UUID attachmentId) {
        return attachmentRepository.findByIdAndAccount(attachmentId, accountId)
                .filter(attachment -> attachment.noteId().equals(noteId))
                .orElseThrow(NoteService::attachmentNotFound);
    }

    /** Takes the per-account advisory lock inside the current transaction (D3). */
    private void lockAccount(UUID accountId) {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (PreparedStatement statement = connection.prepareStatement(ADVISORY_LOCK_SQL)) {
                statement.setString(1, accountId.toString());
                statement.execute();
            }
            return null;
        });
    }

    private static long toMegabytes(long bytes) {
        return bytes / (1024L * 1024L);
    }

    private static ApiException noteNotFound() {
        return ApiException.notFound("note_not_found", "Заметка не найдена");
    }

    private static ApiException attachmentNotFound() {
        return ApiException.notFound("attachment_not_found", "Вложение не найдено");
    }

    private static ApiException invalidAttachment(String message) {
        return ApiException.badRequest("invalid_attachment", message);
    }

    /**
     * Payload and stored name of an attachment, ready to be written into the
     * download response.
     */
    public record AttachmentDownload(String fileName, byte[] content) {

        public AttachmentDownload {
            Objects.requireNonNull(fileName, "fileName");
            Objects.requireNonNull(content, "content");
        }
    }
}
