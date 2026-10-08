-- Dua stored files (Phase 2). Run once in the Supabase SQL editor, or with `supabase db push`.
-- Creates a PRIVATE bucket. Each object lives at "<auth.uid()>/<random uuid>.<ext>", and RLS limits
-- every read, upload and delete to the signed-in user's own folder. The backend calls Storage with the
-- user's own access token, so no service-role key is involved.
--
-- Size: 50 MB per file (52428800 bytes). That is the Supabase Free plan maximum, and the bucket limit
-- cannot go above the project's global limit (Storage > Settings). On Pro and above you can raise both
-- the global and bucket limits, then set DUA_FILES_MAX_MB on Vercel to the same value.
-- Files are immutable: there is no update policy, and uploads use x-upsert: false.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('dua-files', 'dua-files', false, 52428800, array[
 'image/jpeg','image/png','image/webp',
 'application/pdf',
 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet','application/vnd.ms-excel','application/vnd.oasis.opendocument.spreadsheet','text/csv',
 'text/plain','text/markdown','application/json',
 'audio/mp4','audio/m4a','audio/x-m4a','audio/aac','audio/mpeg','audio/mp3','audio/wav','audio/x-wav','audio/wave','audio/ogg','audio/opus','audio/webm','audio/flac'
])
on conflict (id) do update set public = false, file_size_limit = excluded.file_size_limit, allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists dua_files_select_own on storage.objects;
create policy dua_files_select_own on storage.objects for select to authenticated
 using (bucket_id = 'dua-files' and (storage.foldername(name))[1] = (select auth.uid()::text));

drop policy if exists dua_files_insert_own on storage.objects;
create policy dua_files_insert_own on storage.objects for insert to authenticated
 with check (bucket_id = 'dua-files' and (storage.foldername(name))[1] = (select auth.uid()::text));

drop policy if exists dua_files_delete_own on storage.objects;
create policy dua_files_delete_own on storage.objects for delete to authenticated
 using (bucket_id = 'dua-files' and (storage.foldername(name))[1] = (select auth.uid()::text));
