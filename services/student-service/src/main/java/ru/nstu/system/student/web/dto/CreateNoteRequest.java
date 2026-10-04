package ru.nstu.system.student.web.dto;

/**
 * Body of {@code POST /api/notes}. Both fields are validated in the service so a
 * blank or too long title yields the stable {@code invalid_note} code instead of
 * a generic bean-validation error.
 */
public record CreateNoteRequest(String title, String body) {
}
