-- event-service core schema: events, queue entries, transactional outbox.
-- Executed by Flyway into its own schema `event`.
--
-- The event/queue_entry definition follows design.md D15 exactly (fields,
-- defaults, CHECK constraints and partial unique indexes).

-- ---------------------------------------------------------------------------
-- event
-- ---------------------------------------------------------------------------
create table event.event (
    id                 uuid        not null,
    group_id           uuid        not null,
    title              text        not null,
    description        text,
    type               text        not null,               -- only 'QUEUE'
    availability       text        not null,               -- 'GUEST+' | 'STUDENT+' | 'STAFF+'
    starts_at          timestamptz,
    entry_limit        integer     not null default 27,
    entry_unit         text        not null default 'BRIGADE',   -- 'BRIGADE' | 'PERSON'
    journal_visibility text        not null default 'STAFF',     -- 'STAFF' | 'EVERYONE'
    retention_days     integer     not null default 14,
    slug               text        not null,
    status             text        not null default 'OPEN',      -- 'OPEN' | 'CLOSED' | 'ARCHIVED'
    closed_at          timestamptz,
    archived_at        timestamptz,
    archive_payload    bytea,                              -- gzip(jsonb)
    created_by         uuid        not null,
    created_at         timestamptz not null default now(),
    updated_at         timestamptz not null default now(),
    constraint event_pk primary key (id),
    constraint event_type_check check (type in ('QUEUE')),
    constraint event_availability_check check (availability in ('GUEST+', 'STUDENT+', 'STAFF+')),
    constraint event_entry_unit_check check (entry_unit in ('BRIGADE', 'PERSON')),
    constraint event_journal_visibility_check check (journal_visibility in ('STAFF', 'EVERYONE')),
    constraint event_status_check check (status in ('OPEN', 'CLOSED', 'ARCHIVED')),
    constraint event_slug_uniq unique (slug)
);

-- Archivation scheduler scans CLOSED events past their retention window.
create index event_status_closed_at_idx on event.event (status, closed_at);

-- ---------------------------------------------------------------------------
-- queue_entry
-- ---------------------------------------------------------------------------
create table event.queue_entry (
    id                uuid        not null,
    event_id          uuid        not null,
    name              text        not null,
    name_normalized   text        not null,
    position          integer     not null,
    status            text        not null,               -- 'WAITING' | 'PAUSED' | 'PASSED'
    holder_account_id uuid,
    guest_ref         uuid,
    origin            text        not null default 'JOIN', -- 'JOIN' | 'STAFF' | 'CARRY_OVER'
    created_at        timestamptz not null default now(),
    passed_at         timestamptz,
    passed_by         uuid,
    constraint queue_entry_pk primary key (id),
    constraint queue_entry_event_fk foreign key (event_id)
        references event.event (id) on delete cascade,
    constraint queue_entry_status_check check (status in ('WAITING', 'PAUSED', 'PASSED')),
    constraint queue_entry_origin_check check (origin in ('JOIN', 'STAFF', 'CARRY_OVER'))
);

-- Last line of defence against concurrent joins (D16): among *active* entries
-- the name and the position are unique. Passed entries keep their historical
-- position and may reuse names, so they are excluded from the partial indexes.
create unique index queue_entry_active_name_uniq
    on event.queue_entry (event_id, name_normalized)
    where status in ('WAITING', 'PAUSED');

create unique index queue_entry_active_position_uniq
    on event.queue_entry (event_id, position)
    where status in ('WAITING', 'PAUSED');

create index queue_entry_event_status_idx on event.queue_entry (event_id, status);
create index queue_entry_event_passed_at_idx on event.queue_entry (event_id, passed_at);

-- ---------------------------------------------------------------------------
-- outbox (canonical shape from libs/contracts/src/main/resources/db/outbox.sql:
-- `event_id` is the primary key and there is no surrogate `id`; this is what the
-- shared OutboxWriter/JdbcOutboxPublisher insert and select)
-- ---------------------------------------------------------------------------
create table event.outbox (
    event_id     uuid         not null,
    event_type   varchar(255) not null,
    payload      jsonb        not null,
    created_at   timestamptz  not null default now(),
    published_at timestamptz,
    attempts     integer      not null default 0,
    constraint outbox_pk primary key (event_id)
);

create index outbox_published_at_idx on event.outbox (published_at);
