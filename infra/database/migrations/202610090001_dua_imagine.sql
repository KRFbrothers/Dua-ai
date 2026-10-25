-- Dua Imagine (Phase 3). Run once in the Supabase SQL editor (or with `supabase db push`) after
-- 202610080001_dua_files_storage.sql. Safe to run again.
--
-- 1) The private dua-files bucket may also hold generated MP4 videos. Nothing else about the bucket
--    changes: it stays private, keeps its 50 MB per-file limit and its owner-folder policies. If the
--    allowed list was cleared (null = every type allowed), it is left alone.
update storage.buckets
   set allowed_mime_types = pg_catalog.array_append(allowed_mime_types, 'video/mp4')
 where id = 'dua-files'
   and allowed_mime_types is not null
   and not ('video/mp4' = any(allowed_mime_types));

-- 2) Durable daily Imagine usage per account (UTC day): images and video seconds. The backend calls
--    these functions with the signed-in owner's own token; they act on auth.uid() only. A caller can
--    only add to its own usage (amount must be positive) and the limit only decides whether the
--    request is allowed, so the functions cannot be used to reset or raise a cap.
create table if not exists public.dua_imagine_usage (
 owner_id uuid not null references auth.users(id) on delete cascade,
 usage_day date not null,
 images integer not null default 0 check (images >= 0),
 video_seconds integer not null default 0 check (video_seconds >= 0),
 primary key (owner_id, usage_day)
);
alter table public.dua_imagine_usage enable row level security;
revoke all on public.dua_imagine_usage from public, anon, authenticated;
grant select, insert, update, delete on public.dua_imagine_usage to service_role;

create or replace function public.dua_consume_imagine(feature text, amount integer, daily_limit integer)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare
 account_id uuid := auth.uid();
 day_utc date := (pg_catalog.now() at time zone 'UTC')::date;
 used integer;
 reset_epoch bigint;
begin
 if account_id is null then raise exception 'Sign in required' using errcode = '42501'; end if;
 if feature is null or feature not in ('image', 'video') or amount is null or amount < 1 or amount > 100
    or daily_limit is null or daily_limit < 0 or daily_limit > 100000
 then raise exception 'Invalid Imagine usage request' using errcode = '22023'; end if;
 perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended('imagine:' || account_id::text || day_utc::text, 7));
 delete from public.dua_imagine_usage where owner_id = account_id and usage_day < day_utc - 30;
 select case when feature = 'image' then images else video_seconds end into used
   from public.dua_imagine_usage where owner_id = account_id and usage_day = day_utc;
 used := coalesce(used, 0);
 reset_epoch := extract(epoch from ((day_utc + 1)::timestamp at time zone 'UTC'))::bigint;
 if used + amount > daily_limit then
  return jsonb_build_object('allowed', false, 'used', used, 'resetAt', reset_epoch);
 end if;
 insert into public.dua_imagine_usage(owner_id, usage_day, images, video_seconds)
 values (account_id, day_utc, case when feature = 'image' then amount else 0 end, case when feature = 'video' then amount else 0 end)
 on conflict (owner_id, usage_day) do update
   set images = public.dua_imagine_usage.images + excluded.images,
       video_seconds = public.dua_imagine_usage.video_seconds + excluded.video_seconds;
 return jsonb_build_object('allowed', true, 'used', used + amount, 'resetAt', reset_epoch);
end $$;
revoke all on function public.dua_consume_imagine(text, integer, integer) from public, anon;
grant execute on function public.dua_consume_imagine(text, integer, integer) to authenticated, service_role;

create or replace function public.dua_read_imagine_usage()
returns jsonb language plpgsql security definer set search_path = '' as $$
declare
 account_id uuid := auth.uid();
 day_utc date := (pg_catalog.now() at time zone 'UTC')::date;
 image_count integer;
 video_count integer;
begin
 if account_id is null then raise exception 'Sign in required' using errcode = '42501'; end if;
 select images, video_seconds into image_count, video_count
   from public.dua_imagine_usage where owner_id = account_id and usage_day = day_utc;
 return jsonb_build_object('images', coalesce(image_count, 0), 'videoSeconds', coalesce(video_count, 0),
   'resetAt', extract(epoch from ((day_utc + 1)::timestamp at time zone 'UTC'))::bigint);
end $$;
revoke all on function public.dua_read_imagine_usage() from public, anon;
grant execute on function public.dua_read_imagine_usage() to authenticated, service_role;
