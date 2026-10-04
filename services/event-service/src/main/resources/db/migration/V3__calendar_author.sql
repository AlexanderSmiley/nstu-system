-- Calendar: snapshot of the author's display name
-- (change add-preferences-and-calendar-ui; design.md D5).
--
-- The calendar card shows the author's name, but event-service does not own
-- names: it resolves the name once through student-service at creation time and
-- freezes it here. The column is intentionally nullable — an entry whose author
-- had no reachable profile keeps a null name and the card shows the author
-- without one.
--
-- Additive and idempotent: a manual re-run is a no-op and existing rows stay
-- null until they are edited (their author name is never backfilled).
alter table event.calendar_entry
    add column if not exists author_display_name text;
