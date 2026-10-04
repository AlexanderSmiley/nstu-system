-- Durable consumer idempotency (design.md D13).
--
-- Copied from libs/contracts/src/main/resources/db/processed_event.sql and
-- qualified with the `student` schema, mirroring the style of V1. The schema
-- fed to JdbcIdempotencyGuard must match (nstu.idempotency.schema=student).
create table student.processed_event (
    event_id     uuid        not null,
    processed_at timestamptz not null default now(),
    constraint processed_event_pk primary key (event_id)
);
