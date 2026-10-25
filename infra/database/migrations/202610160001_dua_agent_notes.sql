-- Dua Agent self-learning notes (server-side, separate from the app's user-approved Memory).
-- Run once in the Supabase SQL editor (or `supabase db push`). Safe to run again.
-- The Google Cloud engine reads/writes with the signed-in owner's own token; RLS limits every row to auth.uid().
create table if not exists public.agent_notes (
 id uuid primary key default gen_random_uuid(),
 owner_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
 note text not null check (char_length(note) between 1 and 300),
 kind text not null default 'fact' check (kind in ('preference', 'worked', 'failed', 'repo', 'fact')),
 source text not null default 'agent' check (source in ('agent', 'auto')),
 created_at timestamptz not null default now()
);
create index if not exists agent_notes_owner_created on public.agent_notes (owner_id, created_at desc);
alter table public.agent_notes enable row level security;
revoke all on public.agent_notes from public, anon;
grant select, insert, delete on public.agent_notes to authenticated;
grant select, insert, update, delete on public.agent_notes to service_role;

drop policy if exists agent_notes_select on public.agent_notes;
create policy agent_notes_select on public.agent_notes for select to authenticated using (owner_id = auth.uid());
drop policy if exists agent_notes_insert on public.agent_notes;
create policy agent_notes_insert on public.agent_notes for insert to authenticated with check (owner_id = auth.uid());
drop policy if exists agent_notes_delete on public.agent_notes;
create policy agent_notes_delete on public.agent_notes for delete to authenticated using (owner_id = auth.uid());

-- Keep at most 200 notes per owner: the oldest are pruned after each insert.
create or replace function public.agent_notes_prune()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
 delete from public.agent_notes where owner_id = new.owner_id and id in (
   select id from public.agent_notes where owner_id = new.owner_id order by created_at desc, id offset 200);
 return null;
end $$;
revoke all on function public.agent_notes_prune() from public, anon, authenticated;
drop trigger if exists agent_notes_prune on public.agent_notes;
create trigger agent_notes_prune after insert on public.agent_notes for each row execute function public.agent_notes_prune();
