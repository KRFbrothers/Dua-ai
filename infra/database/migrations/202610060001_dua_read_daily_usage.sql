-- Read-only daily usage insights for the authenticated account API.
-- Only the trusted backend may call this RPC.
create or replace function public.dua_read_daily_usage(account_id uuid)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare day_utc date := (pg_catalog.now() at time zone 'UTC')::date; used integer; reset_epoch bigint;
begin
 if account_id is null then raise exception 'Invalid account' using errcode = '22023'; end if;
 select request_count into used from public.dua_daily_usage where owner_id = account_id and usage_day = day_utc;
 used := coalesce(used, 0);
 reset_epoch := extract(epoch from ((day_utc + 1)::timestamp at time zone 'UTC'))::bigint;
 return jsonb_build_object('used', used, 'resetAt', reset_epoch);
end $$;
revoke all on function public.dua_read_daily_usage(uuid) from public, anon, authenticated;
grant execute on function public.dua_read_daily_usage(uuid) to service_role;
