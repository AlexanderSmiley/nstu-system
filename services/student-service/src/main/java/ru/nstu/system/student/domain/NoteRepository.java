package ru.nstu.system.student.domain;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * JDBC access to {@code student.note} (change add-notes-module, design.md D1/D2).
 *
 * <p>Plain JDBC rather than JPA is a deliberate choice: the rows have no
 * relationships to load, and the attachment payloads ({@code bytea}) must never
 * be dragged into a persistence context. Every read is scoped by
 * {@code account_id}, which is what makes notes private.</p>
 */
@Repository
public class NoteRepository {

    private static final String COLUMNS =
            "id, account_id, title, body, created_at, updated_at";

    private static final RowMapper<Note> ROW_MAPPER = (resultSet, rowNumber) -> new Note(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("account_id", UUID.class),
            resultSet.getString("title"),
            resultSet.getString("body"),
            resultSet.getObject("created_at", OffsetDateTime.class).toInstant(),
            resultSet.getObject("updated_at", OffsetDateTime.class).toInstant());

    private final JdbcTemplate jdbcTemplate;

    public NoteRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** @return the caller's notes, most recently updated first */
    public List<Note> findByAccount(UUID accountId) {
        return jdbcTemplate.query(
                "select " + COLUMNS + " from student.note where account_id = ?"
                        + " order by updated_at desc, created_at desc, id",
                ROW_MAPPER,
                accountId);
    }

    /** @return the note only when it belongs to the account; empty otherwise */
    public Optional<Note> findByIdAndAccount(UUID id, UUID accountId) {
        List<Note> notes = jdbcTemplate.query(
                "select " + COLUMNS + " from student.note where id = ? and account_id = ?",
                ROW_MAPPER,
                id,
                accountId);
        return notes.stream().findFirst();
    }

    /** Inserts a new note. The timestamps of the record are persisted verbatim. */
    public void insert(Note note) {
        jdbcTemplate.update(
                "insert into student.note (id, account_id, title, body, created_at, updated_at)"
                        + " values (?, ?, ?, ?, ?, ?)",
                note.id(),
                note.accountId(),
                note.title(),
                note.body(),
                OffsetDateTime.ofInstant(note.createdAt(), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(note.updatedAt(), ZoneOffset.UTC));
    }

    /**
     * Updates title and body of an owned note.
     *
     * @return number of affected rows (0 when the note does not exist or is not owned)
     */
    public int update(UUID id, UUID accountId, String title, String body, Instant updatedAt) {
        return jdbcTemplate.update(
                "update student.note set title = ?, body = ?, updated_at = ?"
                        + " where id = ? and account_id = ?",
                title,
                body,
                OffsetDateTime.ofInstant(updatedAt, ZoneOffset.UTC),
                id,
                accountId);
    }

    /**
     * Deletes an owned note. Attachments are removed by the {@code on delete
     * cascade} foreign key, which also frees their quota.
     *
     * @return number of affected rows (0 when the note does not exist or is not owned)
     */
    public int delete(UUID id, UUID accountId) {
        return jdbcTemplate.update(
                "delete from student.note where id = ? and account_id = ?",
                id,
                accountId);
    }
}
