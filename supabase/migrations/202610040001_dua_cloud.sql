-- Run in the Supabase SQL editor. No service-role key belongs in the app or backend env.
create table if not exists public.dua_cloud_snapshots (
 owner_id uuid primary key references auth.users(id) on delete cascade,
 revision bigint not null default 1 check (revision > 0),
 payload jsonb not null,
 updated_at timestamptz not null default now(),
 constraint dua_cloud_payload_size check (octet_length(payload::text) <= 4194304)
);
alter table public.dua_cloud_snapshots enable row level security;
revoke all on public.dua_cloud_snapshots from anon, authenticated;
grant select on public.dua_cloud_snapshots to authenticated;
drop policy if exists dua_cloud_read_own on public.dua_cloud_snapshots;
create policy dua_cloud_read_own on public.dua_cloud_snapshots for select to authenticated using (owner_id = (select auth.uid()));
-- Writes only through this RPC: direct REST updates cannot bypass revision checks.
create or replace function public.dua_write_cloud_snapshot(expected_revision bigint, next_payload jsonb)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare account uuid := auth.uid(); actual bigint; next_revision bigint;
begin
 if account is null then raise exception 'Authentication required' using errcode = '42501'; end if;
 if expected_revision is null or expected_revision < 0 or expected_revision > 2147483647 or next_payload is null
 or jsonb_typeof(next_payload) is distinct from 'object'
 or next_payload->>'format' is distinct from 'dua-cloud-snapshot'
 or next_payload->>'version' is distinct from '1'
 or jsonb_typeof(next_payload->'chats') is distinct from 'array'
 or jsonb_typeof(next_payload->'memory') is distinct from 'array'
 then raise exception 'Invalid snapshot' using errcode = '22023'; end if;
 if jsonb_array_length(next_payload->'chats') > 100 or jsonb_array_length(next_payload->'memory') > 10
 or octet_length(next_payload::text) > 4194304 then raise exception 'Snapshot too large' using errcode = '22023'; end if;
 perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(account::text, 0));
 select revision into actual from public.dua_cloud_snapshots where owner_id = account;
 actual := coalesce(actual, 0);
 if actual <> expected_revision then return jsonb_build_object('conflict', true, 'revision', actual); end if;
 next_revision := actual + 1;
 insert into public.dua_cloud_snapshots(owner_id, revision, payload, updated_at)
 values (account, next_revision, next_payload, now())
 on conflict(owner_id) do update set revision = excluded.revision, payload = excluded.payload, updated_at = excluded.updated_at;
 return jsonb_build_object('revision', next_revision);
end $$;
revoke all on function public.dua_write_cloud_snapshot(bigint,jsonb) from public, anon;
grant execute on function public.dua_write_cloud_snapshot(bigint,jsonb) to authenticated;
