package ru.nstu.system.student.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A personal note backed by {@code student.note} (change add-notes-module,
 * design.md D2).
 *
 * <p>Reads and writes are always scoped by {@link #accountId()}: a note is
 * invisible to every other account, including administrators. The body is plain
 * text and is never interpreted as markup.</p>
 *
 * @param id        surrogate key
 * @param accountId owner account ({@code auth.account.id})
 * @param title     required, 1..200 characters after trimming
 * @param body      optional plain text, or {@code null}
 * @param createdAt creation timestamp
 * @param updatedAt last modification timestamp
 */
public record Note(
        UUID id,
        UUID accountId,
        String title,
        String body,
        Instant createdAt,
        Instant updatedAt) {

    public Note {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
