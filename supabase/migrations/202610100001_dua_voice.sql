-- Dua live voice (Phase 4B). Run once in the Supabase SQL editor (or with `supabase db push`) after
-- 202610090001_dua_imagine.sql. Safe to run again. Earlier migrations are not changed.
--
-- Durable daily live-voice minutes per account (UTC day). Each live session reserves time when the backend
-- mints its xAI token; the reservation counts against the cap until the session ends. Ending charges the
-- wall-clock time used (never more than the reservation) and frees the rest. A session that is never ended
-- (app killed, no network) is charged its full reservation once it is overdue, so spend is never under-counted.
-- The backend calls these functions with the signed-in owner's own token; they act on auth.uid() only and a
-- caller cannot lower its usage, extend a reservation or raise the cap.
create table if not exists public.dua_voice_usage (
 owner_id uuid not null references auth.users(id) on delete cascade,
 usage_day date not null,
 seconds integer not null default 0 check (seconds >= 0),
 primary key (owner_id, usage_day)
);
alter table public.dua_voice_usage enable row level security;
revoke all on public.dua_voice_usage from public, anon, authenticated;
grant select, insert, update, delete on public.dua_voice_usage to service_role;

create table if not exists public.dua_voice_sessions (
 id uuid primary key,
 owner_id uuid not null references auth.users(id) on delete cascade,
 usage_day date not null,
 reserved_seconds integer not null check (reserved_seconds between 1 and 7200),
 started_at timestamptz not null default pg_catalog.now(),
 ended_at timestamptz,
 charged_seconds integer check (charged_seconds >= 0)
);
create index if not exists dua_voice_sessions_open on public.dua_voice_sessions(owner_id) where ended_at is null;
alter table public.dua_voice_sessions enable row level security;
revoke all on public.dua_voice_sessions from public, anon, authenticated;
grant select, insert, update, delete on public.dua_voice_sessions to service_role;

-- Charges overdue open sessions of one account their full reservation (internal helper, not callable by clients).
create or replace function public.dua_voice_settle_overdue(account_id uuid)
returns void language plpgsql security definer set search_path = '' as $$
declare
 overdue record;
begin
 for overdue in
  update public.dua_voice_sessions
     set ended_at = pg_catalog.now(), charged_seconds = reserved_seconds
   where owner_id = account_id and ended_at is null
     and started_at + pg_catalog.make_interval(secs => reserved_seconds + 120) < pg_catalog.now()
  returning usage_day, reserved_seconds
 loop
  insert into public.dua_voice_usage(owner_id, usage_day, seconds) values (account_id, overdue.usage_day, overdue.reserved_seconds)
  on conflict (owner_id, usage_day) do update set seconds = public.dua_voice_usage.seconds + excluded.seconds;
 end loop;
end $$;
revoke all on function public.dua_voice_settle_overdue(uuid) from public, anon, authenticated;

create or replace function public.dua_start_voice_session(session_id uuid, reserve_seconds integer, daily_limit_seconds integer, min_seconds integer)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare
 account_id uuid := auth.uid();
 day_utc date := (pg_catalog.now() at time zone 'UTC')::date;
 used integer;
 active integer;
 remaining integer;
 granted integer;
 reset_epoch bigint;
begin
 if account_id is null then raise exception 'Sign in required' using errcode = '42501'; end if;
 if session_id is null or reserve_seconds is null or reserve_seconds < 1 or reserve_seconds > 7200
    or daily_limit_seconds is null or daily_limit_seconds < 0 or daily_limit_seconds > 86400
    or min_seconds is null or min_seconds < 1 or min_seconds > reserve_seconds
 then raise exception 'Invalid voice session request' using errcode = '22023'; end if;
 perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended('voice:' || account_id::text, 7));
 perform public.dua_voice_settle_overdue(account_id);
 delete from public.dua_voice_usage where owner_id = account_id and usage_day < day_utc - 30;
 delete from public.dua_voice_sessions where owner_id = account_id and ended_at is not null and usage_day < day_utc - 30;
 select seconds into used from public.dua_voice_usage where owner_id = account_id and usage_day = day_utc;
 used := coalesce(used, 0);
 select coalesce(sum(reserved_seconds), 0) into active from public.dua_voice_sessions
  where owner_id = account_id and ended_at is null and usage_day = day_utc;
 reset_epoch := extract(epoch from ((day_utc + 1)::timestamp at time zone 'UTC'))::bigint;
 remaining := greatest(0, daily_limit_seconds - used - active);
 if remaining < min_seconds then
  return jsonb_build_object('allowed', false, 'usedSeconds', used, 'activeSeconds', active, 'limitSeconds', daily_limit_seconds, 'resetAt', reset_epoch);
 end if;
 if exists (select 1 from public.dua_voice_sessions where id = session_id) then
  raise exception 'Voice session already exists' using errcode = '23505';
 end if;
 granted := least(reserve_seconds, remaining);
 insert into public.dua_voice_sessions(id, owner_id, usage_day, reserved_seconds) values (session_id, account_id, day_utc, granted);
 return jsonb_build_object('allowed', true, 'grantedSeconds', granted, 'usedSeconds', used, 'activeSeconds', active + granted,
   'limitSeconds', daily_limit_seconds, 'resetAt', reset_epoch);
end $$;
revoke all on function public.dua_start_voice_session(uuid, integer, integer, integer) from public, anon;
grant execute on function public.dua_start_voice_session(uuid, integer, integer, integer) to authenticated, service_role;

create or replace function public.dua_end_voice_session(session_id uuid)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare
 account_id uuid := auth.uid();
 found_session public.dua_voice_sessions%rowtype;
 charge integer;
 used integer;
begin
 if account_id is null then raise exception 'Sign in required' using errcode = '42501'; end if;
 if session_id is null then raise exception 'Invalid voice session' using errcode = '22023'; end if;
 perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended('voice:' || account_id::text, 7));
 select * into found_session from public.dua_voice_sessions where id = session_id and owner_id = account_id;
 if not found then return jsonb_build_object('found', false); end if;
 if found_session.ended_at is null then
  charge := least(found_session.reserved_seconds,
    greatest(0, ceil(extract(epoch from (pg_catalog.now() - found_session.started_at)))::integer));
  update public.dua_voice_sessions set ended_at = pg_catalog.now(), charged_seconds = charge where id = session_id;
  insert into public.dua_voice_usage(owner_id, usage_day, seconds) values (account_id, found_session.usage_day, charge)
  on conflict (owner_id, usage_day) do update set seconds = public.dua_voice_usage.seconds + excluded.seconds;
 else
  charge := found_session.charged_seconds;
 end if;
 select seconds into used from public.dua_voice_usage where owner_id = account_id and usage_day = found_session.usage_day;
 return jsonb_build_object('found', true, 'chargedSeconds', coalesce(charge, 0), 'usedSeconds', coalesce(used, 0));
end $$;
revoke all on function public.dua_end_voice_session(uuid) from public, anon;
grant execute on function public.dua_end_voice_session(uuid) to authenticated, service_role;

create or replace function public.dua_read_voice_usage()
returns jsonb language plpgsql security definer set search_path = '' as $$
declare
 account_id uuid := auth.uid();
 day_utc date := (pg_catalog.now() at time zone 'UTC')::date;
 used integer;
 active integer;
begin
 if account_id is null then raise exception 'Sign in required' using errcode = '42501'; end if;
 select seconds into used from public.dua_voice_usage where owner_id = account_id and usage_day = day_utc;
 select coalesce(sum(reserved_seconds), 0) into active from public.dua_voice_sessions
  where owner_id = account_id and ended_at is null and usage_day = day_utc
    and started_at + pg_catalog.make_interval(secs => reserved_seconds + 120) >= pg_catalog.now();
 return jsonb_build_object('usedSeconds', coalesce(used, 0), 'activeSeconds', active,
   'resetAt', extract(epoch from ((day_utc + 1)::timestamp at time zone 'UTC'))::bigint);
end $$;
revoke all on function public.dua_read_voice_usage() from public, anon;
grant execute on function public.dua_read_voice_usage() to authenticated, service_role;
