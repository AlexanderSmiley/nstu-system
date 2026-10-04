-- auth-service core schema: accounts, refresh tokens, transactional outbox.
-- Executed by Flyway into its own schema `auth` (see spring.flyway.schemas).
-- Idempotency of the *run* is guaranteed by Flyway's schema history table; the
-- DDL below intentionally does not use `IF NOT EXISTS` so that accidental
-- reruns against a partially migrated schema fail loudly instead of silently.

-- ---------------------------------------------------------------------------
-- account
-- ---------------------------------------------------------------------------
create table auth.account (
    id                   uuid         not null,
    username             varchar(64)  not null,
    username_normalized  varchar(64)  not null,
    password_hash        varchar(100) not null,
    display_name         varchar(255),
    email                varchar(255),
    role                 varchar(16)  not null,
    blocked              boolean      not null default false,
    must_change_password boolean      not null default true,
    created_at           timestamptz  not null default now(),
    updated_at           timestamptz  not null default now(),
    constraint account_pk primary key (id),
    -- GUEST is never persisted: it only exists as an anonymous session role.
    constraint account_role_check check (role in ('ADMIN', 'STAFF', 'STUDENT')),
    constraint account_username_uniq unique (username),
    -- Case-insensitive uniqueness/lookup for usernames.
    constraint account_username_normalized_uniq unique (username_normalized)
);

-- Enforces the "exactly one ADMIN" invariant at the database level (identity
-- spec "Единственный администратор"). Uniqueness of `role` restricted to the
-- ADMIN subset means at most one such row can ever exist, even when two
-- instances race during bootstrap with different ADMIN_USERNAME values.
create unique index account_single_admin_uniq on auth.account (role) where role = 'ADMIN';

-- ---------------------------------------------------------------------------
-- refresh_token (opaque token; only its hash is stored)
-- ---------------------------------------------------------------------------
create table auth.refresh_token (
    id           uuid         not null,
    account_id   uuid         not null,
    token_hash   varchar(128) not null,
    expires_at   timestamptz  not null,
    revoked      boolean      not null default false,
    rotated_from uuid,
    created_at   timestamptz  not null default now(),
    constraint refresh_token_pk primary key (id),
    constraint refresh_token_account_fk foreign key (account_id)
        references auth.account (id) on delete cascade
);

create index refresh_token_account_id_idx on auth.refresh_token (account_id);
-- Unique: a token hash identifies at most one token, so `findByTokenHash` can
-- never observe duplicate rows. Refresh is backed by a cryptographically random
-- opaque token, hence hash collisions are not an expected condition.
create unique index refresh_token_token_hash_idx on auth.refresh_token (token_hash);

-- ---------------------------------------------------------------------------
-- outbox (transactional outbox, identical shape in every service schema)
-- ---------------------------------------------------------------------------
create table auth.outbox (
    id           uuid         not null,
    event_id     uuid         not null,
    event_type   varchar(255) not null,
    payload      jsonb        not null,
    created_at   timestamptz  not null default now(),
    published_at timestamptz,
    attempts     integer      not null default 0,
    constraint outbox_pk primary key (id),
    constraint outbox_event_id_uniq unique (event_id)
);

create index outbox_published_at_idx on auth.outbox (published_at);
