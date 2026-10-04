-- Optional durable per-account daily request budget. Execute as database owner.
-- Only the trusted backend may call this RPC; clients cannot choose their own limit.
create table if not exists public.dua_daily_usage (
 owner_id uuid not null references auth.users(id) on delete cascade,
 usage_day date not null,
 request_count integer not null default 0 check (request_count >= 0),
 primary key (owner_id, usage_day)
);
alter table public.dua_daily_usage enable row level security;
revoke all on public.dua_daily_usage from public, anon, authenticated;
grant select, insert, update, delete on public.dua_daily_usage to service_role;
create or replace function public.dua_consume_daily_request(account_id uuid, daily_limit integer)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare day_utc date := (pg_catalog.now() at time zone 'UTC')::date; used integer; reset_epoch bigint;
begin
 if account_id is null or daily_limit is null or daily_limit < 1 or daily_limit > 100000
 then raise exception 'Invalid daily request budget' using errcode = '22023'; end if;
 perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(account_id::text || day_utc::text, 7));
 delete from public.dua_daily_usage where owner_id = account_id and usage_day < day_utc - 30;
 select request_count into used from public.dua_daily_usage where owner_id = account_id and usage_day = day_utc;
 used := coalesce(used, 0);
 reset_epoch := extract(epoch from ((day_utc + 1)::timestamp at time zone 'UTC'))::bigint;
 if used >= daily_limit then return jsonb_build_object('allowed', false, 'used', used, 'resetAt', reset_epoch); end if;
 insert into public.dua_daily_usage(owner_id, usage_day, request_count) values (account_id, day_utc, used + 1)
 on conflict(owner_id, usage_day) do update set request_count = excluded.request_count;
 return jsonb_build_object('allowed', true, 'used', used + 1, 'resetAt', reset_epoch);
end $$;
revoke all on function public.dua_consume_daily_request(uuid,integer) from public, anon, authenticated;
grant execute on function public.dua_consume_daily_request(uuid,integer) to service_role;
