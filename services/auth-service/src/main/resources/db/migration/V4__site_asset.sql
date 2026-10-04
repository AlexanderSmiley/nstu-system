-- Site assets stored in the database (design.md D1, change add-site-icon).
--
-- The only asset in the MVP is the site icon (`kind = 'icon'`). Binary content
-- lives in the row so no S3 bucket or shared file volume is required; `kind` is
-- the primary key, so at most one icon can ever exist and a reset simply deletes
-- the row. The 256 KB application-level limit keeps the table small.

create table if not exists auth.site_asset (
    kind         varchar(32)  not null,
    content_type varchar(128) not null,
    bytes        bytea        not null,
    size_bytes   integer      not null,
    updated_at   timestamptz  not null default now(),
    updated_by   uuid,
    constraint site_asset_pk primary key (kind),
    constraint site_asset_size_check check (size_bytes >= 0),
    constraint site_asset_updated_by_fk foreign key (updated_by)
        references auth.account (id) on delete set null
);
