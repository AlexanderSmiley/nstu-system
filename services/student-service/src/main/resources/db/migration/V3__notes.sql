-- Personal notes module (change add-notes-module, design.md D1/D2).
--
-- Notes belong to a single auth account and are private: every query filters by
-- account_id. Attachments are stored inline as bytea (no S3), bounded by the
-- per-user quota enforced in NoteService, so the whole feature lives in the
-- existing `student` schema and needs no new container.
--
-- `if not exists` keeps a manual re-apply idempotent; Flyway itself guarantees a
-- single execution per history row.

-- ---------------------------------------------------------------------------
-- note
-- ---------------------------------------------------------------------------
create table if not exists student.note (
    id         uuid         not null,
    account_id uuid         not null,
    title      varchar(200) not null,
    body       text,
    created_at timestamptz  not null default now(),
    updated_at timestamptz  not null default now(),
    constraint note_pk primary key (id)
);

-- Primary access path: "list my notes newest first".
create index if not exists note_account_updated_idx
    on student.note (account_id, updated_at desc);

-- ---------------------------------------------------------------------------
-- note_attachment
-- ---------------------------------------------------------------------------
create table if not exists student.note_attachment (
    id           uuid         not null,
    note_id      uuid         not null,
    file_name    varchar(255) not null,
    content_type varchar(255) not null,
    size_bytes   bigint       not null,
    bytes        bytea        not null,
    created_at   timestamptz  not null default now(),
    constraint note_attachment_pk primary key (id),
    -- Deleting a note removes its attachments and frees the quota in one step.
    constraint note_attachment_note_fk foreign key (note_id)
        references student.note (id) on delete cascade
);

create index if not exists note_attachment_note_id_idx
    on student.note_attachment (note_id);
