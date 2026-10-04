package ru.nstu.system.student.web.dto;

import java.util.List;

/**
 * Body of {@code GET /api/notes} (change add-notes-module, design.md D4): the
 * caller's notes together with the storage quota so the UI can render the
 * "used X of Y" indicator in a single request.
 */
public record NoteListResponse(List<NoteResponse> notes, NoteQuotaResponse quota) {
}
