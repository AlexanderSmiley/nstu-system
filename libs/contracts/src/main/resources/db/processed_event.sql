-- DDL fragment for durable consumer idempotency (design.md D13).
--
-- Reuse in the Flyway migration of every consuming service. The migration runs
-- with the service schema selected (spring.flyway.schemas / default-schema), so
-- the unqualified table name lands in that schema. If a migration does not set
-- the schema, qualify the name explicitly, e.g. event.processed_event.
--
-- The schema fed to JdbcIdempotencyGuard must match (nstu.idempotency.schema).
CREATE TABLE IF NOT EXISTS processed_event (
    event_id     uuid PRIMARY KEY,
    processed_at timestamptz NOT NULL DEFAULT now()
);
