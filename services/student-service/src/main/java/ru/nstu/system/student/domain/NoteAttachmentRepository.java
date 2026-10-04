package ru.nstu.system.student.domain;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * JDBC access to {@code student.note_attachment} (change add-notes-module,
 * design.md D2/D5).
 *
 * <p>All metadata queries join {@code student.note} and filter by
 * {@code account_id}, so callers cannot observe attachments of another account.
 * The {@code bytes} column is read only by {@link #findContent(UUID, UUID)}.</p>
 */
@Repository
public class NoteAttachmentRepository {

    private static final String METADATA_COLUMNS =
            "a.id, a.note_id, a.file_name, a.content_type, a.size_bytes, a.created_at";

    private static final RowMapper<NoteAttachment> ROW_MAPPER = (resultSet, rowNumber) -> new NoteAttachment(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("note_id", UUID.class),
            resultSet.getString("file_name"),
            resultSet.getString("content_type"),
            resultSet.getLong("size_bytes"),
            resultSet.getObject("created_at", OffsetDateTime.class).toInstant());

    private final JdbcTemplate jdbcTemplate;

    public NoteAttachmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** @return every attachment of the account, oldest first */
    public List<NoteAttachment> findByAccount(UUID accountId) {
        return jdbcTemplate.query(
                "select " + METADATA_COLUMNS + " from student.note_attachment a"
                        + " join student.note n on n.id = a.note_id"
                        + " where n.account_id = ? order by a.created_at, a.id",
                ROW_MAPPER,
                accountId);
    }

    /** @return attachments of one owned note, oldest first */
    public List<NoteAttachment> findByNoteAndAccount(UUID noteId, UUID accountId) {
        return jdbcTemplate.query(
                "select " + METADATA_COLUMNS + " from student.note_attachment a"
                        + " join student.note n on n.id = a.note_id"
                        + " where a.note_id = ? and n.account_id = ? order by a.created_at, a.id",
                ROW_MAPPER,
                noteId,
                accountId);
    }

    /** @return metadata of an attachment only when its note belongs to the account */
    public Optional<NoteAttachment> findByIdAndAccount(UUID id, UUID accountId) {
        List<NoteAttachment> rows = jdbcTemplate.query(
                "select " + METADATA_COLUMNS + " from student.note_attachment a"
                        + " join student.note n on n.id = a.note_id"
                        + " where a.id = ? and n.account_id = ?",
                ROW_MAPPER,
                id,
                accountId);
        return rows.stream().findFirst();
    }

    /** @return the payload of an owned attachment, or empty when it does not exist */
    public Optional<byte[]> findContent(UUID id, UUID accountId) {
        List<byte[]> rows = jdbcTemplate.query(
                "select a.bytes from student.note_attachment a"
                        + " join student.note n on n.id = a.note_id"
                        + " where a.id = ? and n.account_id = ?",
                (resultSet, rowNumber) -> resultSet.getBytes("bytes"),
                id,
                accountId);
        return rows.stream().findFirst();
    }

    /** @return total attachment bytes of the account, used for the quota check */
    public long sumSizeByAccount(UUID accountId) {
        Long sum = jdbcTemplate.queryForObject(
                "select coalesce(sum(a.size_bytes), 0) from student.note_attachment a"
                        + " join student.note n on n.id = a.note_id"
                        + " where n.account_id = ?",
                Long.class,
                accountId);
        return sum == null ? 0L : sum;
    }

    /** Inserts an attachment; the caller owns the surrounding transaction and quota lock. */
    public void insert(NoteAttachment attachment, byte[] content) {
        jdbcTemplate.update(
                "insert into student.note_attachment"
                        + " (id, note_id, file_name, content_type, size_bytes, bytes, created_at)"
                        + " values (?, ?, ?, ?, ?, ?, ?)",
                attachment.id(),
                attachment.noteId(),
                attachment.fileName(),
                attachment.contentType(),
                attachment.sizeBytes(),
                content,
                OffsetDateTime.ofInstant(attachment.createdAt(), ZoneOffset.UTC));
    }

    /**
     * Deletes an owned attachment.
     *
     * @return number of affected rows (0 when the attachment does not exist or is not owned)
     */
    public int delete(UUID id, UUID accountId) {
        return jdbcTemplate.update(
                "delete from student.note_attachment a using student.note n"
                        + " where a.note_id = n.id and a.id = ? and n.account_id = ?",
                id,
                accountId);
    }
}
