-- Calendar module: one day-scoped entry with an audience
-- (change add-calendar-module; design.md D2).
--
-- Dates are stored as calendar values without a time zone: a single study group
-- works in one time zone and TZ arithmetic at the day boundary is a source of
-- bugs. The migration is additive and idempotent, so a manual re-run is a no-op.
create table if not exists event.calendar_entry (
    id                uuid        not null,
    group_id          uuid        not null,
    author_account_id uuid        not null,
    title             text        not null,
    description       text,
    starts_on         date        not null,
    starts_at         time,
    audience          text        not null default 'ME',
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    constraint calendar_entry_pk primary key (id),
    constraint calendar_entry_audience_check check (audience in ('ME', 'GROUP', 'STAFF'))
);

-- The only query pattern is "entries of a group in a date window", so a single
-- composite index on (group_id, starts_on) serves it.
create index if not exists calendar_entry_group_starts_on_idx
    on event.calendar_entry (group_id, starts_on);
