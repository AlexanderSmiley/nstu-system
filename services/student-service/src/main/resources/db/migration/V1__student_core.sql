-- student-service core schema: study groups, student profiles, transactional outbox.
-- Executed by Flyway into its own schema `student`.

-- ---------------------------------------------------------------------------
-- app_group
-- ---------------------------------------------------------------------------
create table student.app_group (
    id         uuid         not null,
    name       varchar(255) not null,
    created_at timestamptz  not null default now(),
    updated_at timestamptz  not null default now(),
    constraint app_group_pk primary key (id)
);

-- Seed the single default group. The literal must match
-- ru.nstu.system.contracts.Groups#DEFAULT_GROUP_ID.
insert into student.app_group (id, name)
values ('592983db-f966-445e-8aca-a099153f78cf', 'НГТУ — группа по умолчанию')
on conflict (id) do nothing;

-- ---------------------------------------------------------------------------
-- student_profile (personal data lives only here; id equals auth.account.id)
--
-- Email is deliberately absent: the customer decision is that the email PII is
-- stored only in auth.account, never duplicated in the profile.
-- ---------------------------------------------------------------------------
create table student.student_profile (
    id         uuid         not null,
    full_name  varchar(255) not null,
    group_id   uuid         not null,
    -- Contacts are an open-ended set (phone, telegram, ...). jsonb keeps the MVP
    -- flexible without a schema migration for every new contact kind, while
    -- still validating the payload as JSON on write.
    contacts   jsonb,
    created_at timestamptz  not null default now(),
    updated_at timestamptz  not null default now(),
    constraint student_profile_pk primary key (id),
    constraint student_profile_group_fk foreign key (group_id)
        references student.app_group (id)
);

create index student_profile_group_id_idx on student.student_profile (group_id);

-- ---------------------------------------------------------------------------
-- outbox (canonical shape from libs/contracts/src/main/resources/db/outbox.sql:
-- `event_id` is the primary key and there is no surrogate `id`; this is what the
-- shared OutboxWriter/JdbcOutboxPublisher expect)
-- ---------------------------------------------------------------------------
create table student.outbox (
    event_id     uuid         not null,
    event_type   varchar(255) not null,
    payload      jsonb        not null,
    created_at   timestamptz  not null default now(),
    published_at timestamptz,
    attempts     integer      not null default 0,
    constraint outbox_pk primary key (event_id)
);

create index outbox_published_at_idx on student.outbox (published_at);
