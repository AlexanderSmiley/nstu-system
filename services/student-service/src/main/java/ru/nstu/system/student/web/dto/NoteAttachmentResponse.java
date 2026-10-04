package ru.nstu.system.student.web.dto;

import java.time.Instant;
import java.util.UUID;
import ru.nstu.system.student.domain.NoteAttachment;

/**
 * Attachment metadata returned inside a {@link NoteResponse} (change
 * add-notes-module, design.md D4). The payload is served separately by
 * {@code GET /api/notes/{id}/attachments/{attachmentId}}.
 */
public record NoteAttachmentResponse(
        UUID id,
        String fileName,
        String contentType,
        long sizeBytes,
        Instant createdAt) {

    public static NoteAttachmentResponse from(NoteAttachment attachment) {
        return new NoteAttachmentResponse(
                attachment.id(),
                attachment.fileName(),
                attachment.contentType(),
                attachment.sizeBytes(),
                attachment.createdAt());
    }
}
