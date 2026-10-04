package ru.nstu.system.student.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Metadata of a note attachment backed by {@code student.note_attachment}
 * (change add-notes-module, design.md D2/D5).
 *
 * <p>The binary payload is deliberately not part of this record: listings and
 * metadata queries must never pull {@code bytes} into memory. The payload is
 * fetched only for a download, through
 * {@link NoteAttachmentRepository#findContent(UUID, UUID)}.</p>
 *
 * @param id          surrogate key
 * @param noteId      owning note
 * @param fileName    sanitised file name (path separators and control characters removed)
 * @param contentType stored MIME type; served as {@code application/octet-stream}
 * @param sizeBytes   payload length in bytes
 * @param createdAt   creation timestamp
 */
public record NoteAttachment(
        UUID id,
        UUID noteId,
        String fileName,
        String contentType,
        long sizeBytes,
        Instant createdAt) {

    public NoteAttachment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(noteId, "noteId");
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(contentType, "contentType");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
