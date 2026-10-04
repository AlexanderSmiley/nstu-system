package ru.nstu.system.student.web.dto;

/**
 * Body of {@code PATCH /api/notes/{id}}: an absent ({@code null}) field keeps the
 * current value. An empty body string clears the body; the frontend always sends
 * the full editor state on save.
 */
public record UpdateNoteRequest(String title, String body) {
}
