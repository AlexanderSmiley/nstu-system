-- Align auth.outbox with the shared contract in libs/contracts (db/outbox.sql).
--
-- V1 declared a surrogate `id uuid not null` primary key next to `event_id`,
-- but the shared OutboxWriter (ru.nstu:contracts) inserts only
-- (event_id, event_type, payload, created_at) and never populates `id`, so every
-- write failed on the not-null constraint. The canonical shared DDL makes
-- `event_id` the primary key and has no `id` column; this migration removes the
-- extra column to match it, without touching the already-applied V1/V2 history.

alter table auth.outbox drop constraint outbox_event_id_uniq;
alter table auth.outbox drop constraint outbox_pk;
alter table auth.outbox drop column id;
alter table auth.outbox add constraint outbox_pk primary key (event_id);
