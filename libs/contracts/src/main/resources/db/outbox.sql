-- DDL fragment for the transactional outbox (design.md D13, D15).
--
-- Reuse in the Flyway migration of every publishing service (auth, student,
-- event). The migration runs with the service schema selected, so the
-- unqualified table name lands in that schema; otherwise qualify it explicitly,
-- e.g. auth.outbox. The schema fed to OutboxWriter / JdbcOutboxPublisher must
-- match (nstu.outbox.schema).
--
-- `payload` stores the whole DomainEvent envelope as jsonb, which preserves
-- version and occurredAt for the publisher.
CREATE TABLE IF NOT EXISTS outbox (
    event_id     uuid PRIMARY KEY,
    event_type   text NOT NULL,
    payload      jsonb NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    published_at timestamptz,
    attempts     int NOT NULL DEFAULT 0
);

-- The publisher selects unpublished rows in created_at order.
CREATE INDEX IF NOT EXISTS idx_outbox_unpublished
    ON outbox (created_at)
    WHERE published_at IS NULL;
