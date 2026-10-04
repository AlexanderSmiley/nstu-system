package ru.nstu.system.student.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import ru.nstu.system.student.domain.Note;
import ru.nstu.system.student.domain.NoteAttachment;

/**
 * One note of {@code GET /api/notes} (change add-notes-module, design.md D4).
 *
 * <p>{@code body} is plain text; the client must render it as text and never as
 * markup.</p>
 */
public record NoteResponse(
        UUID id,
        String title,
        String body,
        Instant createdAt,
        Instant updatedAt,
        List<NoteAttachmentResponse> attachments) {

    public static NoteResponse from(Note note, List<NoteAttachment> attachments) {
        return new NoteResponse(
                note.id(),
                note.title(),
                note.body(),
                note.createdAt(),
                note.updatedAt(),
                attachments.stream().map(NoteAttachmentResponse::from).toList());
    }
}
