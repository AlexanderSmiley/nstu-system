-- Site settings (key/value). In MVP the only entry is `site.name` (D27).

create table auth.site_setting (
    key        varchar(64) not null,
    value      jsonb       not null,
    updated_at timestamptz not null default now(),
    updated_by uuid,
    constraint site_setting_pk primary key (key),
    constraint site_setting_updated_by_fk foreign key (updated_by)
        references auth.account (id) on delete set null
);

-- Idempotent seed: re-running the migration (e.g. after a partial failure) must
-- not fail and must not overwrite an operator-provided value.
insert into auth.site_setting (key, value)
values ('site.name', to_jsonb('NSTU System'::text))
on conflict (key) do nothing;
