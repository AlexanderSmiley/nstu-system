package ru.nstu.system.student.web.dto;

/**
 * Storage usage of the caller's notes (change add-notes-module, design.md D4).
 *
 * @param usedBytes  total attachment bytes across all of the account's notes
 * @param limitBytes configured quota ({@code nstu.notes.quota-bytes})
 */
public record NoteQuotaResponse(long usedBytes, long limitBytes) {
}
