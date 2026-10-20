-- Run in Supabase SQL editor. Each authenticated account can access only its own backup row.
create table if not exists public.fitwave_backups (
  user_id uuid primary key references auth.users(id) on delete cascade,
  payload jsonb not null,
  schema_version integer not null default 1,
  updated_at timestamptz not null default now()
);
alter table public.fitwave_backups enable row level security;
drop policy if exists "Owner can select own backup" on public.fitwave_backups;
create policy "Owner can select own backup" on public.fitwave_backups
 for select to authenticated using ((select auth.uid()) = user_id);
drop policy if exists "Owner can insert own backup" on public.fitwave_backups;
create policy "Owner can insert own backup" on public.fitwave_backups
 for insert to authenticated with check ((select auth.uid()) = user_id);
drop policy if exists "Owner can update own backup" on public.fitwave_backups;
create policy "Owner can update own backup" on public.fitwave_backups
 for update to authenticated using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
-- Server chooses updated_at. Prevent the client from spoofing the ordering.
create or replace function public.fitwave_backup_touch() returns trigger language plpgsql as $$
begin new.updated_at=now();return new;end $$;
drop trigger if exists fitwave_backup_touch on public.fitwave_backups;
create trigger fitwave_backup_touch before insert or update on public.fitwave_backups
for each row execute function public.fitwave_backup_touch();
