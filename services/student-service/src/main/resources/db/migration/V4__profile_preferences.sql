-- Per-account UI preferences (change add-preferences-and-calendar-ui, design.md D2):
-- which home-page modules are enabled and the three calendar audience colours.
--
-- Preferences belong to the profile (one row per profile, sharing its primary
-- key). The profile itself is created for every account, including ADMIN, so
-- every account can own preferences. A missing row is created lazily by the
-- service with these defaults, so the column defaults are the single source of
-- truth for a first read.
--
-- `if not exists` keeps a manual re-apply idempotent; Flyway itself guarantees a
-- single execution per history row.
create table if not exists student.profile_preferences (
    profile_id      uuid        not null,
    -- {"events": true, "calendar": true, "notes": true}
    modules         jsonb       not null default
        '{"events": true, "calendar": true, "notes": true}'::jsonb,
    -- {"ME": "#ffffff", "GROUP": "#cfe3ff", "STAFF": "#d9dde3"}
    calendar_colors jsonb       not null default
        '{"ME": "#ffffff", "GROUP": "#cfe3ff", "STAFF": "#d9dde3"}'::jsonb,
    updated_at      timestamptz not null default now(),
    constraint profile_preferences_pk primary key (profile_id),
    -- Deleting the profile removes its preferences in the same step.
    constraint profile_preferences_profile_fk foreign key (profile_id)
        references student.student_profile (id) on delete cascade
);
