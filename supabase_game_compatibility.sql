-- GMailGPU Game Compatibility Library (v0.3 Beta)
-- Run once in Supabase SQL Editor. Community reports are explicitly labeled unverified until reviewed.
create extension if not exists pgcrypto;

create table if not exists public.game_compatibility_reports (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  username text not null default 'community member',
  game_name text not null,
  game_version text,
  gpu_family text not null check (gpu_family in ('Mali-G52','Mali-G57','Mali-G615','Mali-G720','Other')),
  device_model text,
  soc text,
  android_version text,
  driver_type text not null check (driver_type in ('System Driver','PanVK','Other')),
  driver_version text,
  emulator_name text not null,
  emulator_version text,
  wine_version text,
  dxvk_version text,
  vkd3d_version text,
  box64_version text,
  resolution text,
  graphics_preset text,
  average_fps numeric(7,2),
  minimum_fps numeric(7,2),
  outcome text not null check (outcome in ('Playable','Playable with issues','Crashes / does not launch','Not tested')),
  graphics_issues text,
  notes text not null,
  evidence_paths text[] not null default '{}',
  review_status text not null default 'community' check (review_status in ('community','verified','rejected')),
  created_at timestamptz not null default now(),
  constraint game_compatibility_game_name_not_blank check (length(trim(game_name)) > 0),
  constraint game_compatibility_notes_not_blank check (length(trim(notes)) > 0),
  constraint game_compatibility_evidence_required check (cardinality(evidence_paths) >= 1)
);

create index if not exists game_compatibility_game_gpu_idx
  on public.game_compatibility_reports (lower(game_name), gpu_family, created_at desc);
create index if not exists game_compatibility_driver_idx
  on public.game_compatibility_reports (lower(game_name), gpu_family, driver_type);

alter table public.game_compatibility_reports enable row level security;
grant select, insert, update on public.game_compatibility_reports to authenticated;

drop policy if exists "Read community game reports" on public.game_compatibility_reports;
create policy "Read community game reports"
  on public.game_compatibility_reports for select
  to authenticated
  using (review_status <> 'rejected' or user_id = auth.uid());

drop policy if exists "Submit own game report" on public.game_compatibility_reports;
create policy "Submit own game report"
  on public.game_compatibility_reports for insert
  to authenticated
  with check (user_id = auth.uid() and review_status = 'community');

create or replace function public.set_game_report_submitter()
returns trigger
language plpgsql
security definer
set search_path = public
as $function$
begin
  select p.username into new.username
  from public.profiles p
  where p.id = auth.uid();

  if new.username is null or length(trim(new.username)) = 0 then
    new.username := 'community member';
  end if;

  new.review_status := 'community';
  return new;
end;
$function$;

drop trigger if exists set_game_report_submitter on public.game_compatibility_reports;
create trigger set_game_report_submitter
before insert on public.game_compatibility_reports
for each row execute function public.set_game_report_submitter();

drop policy if exists "Update own community game report" on public.game_compatibility_reports;
create policy "Update own community game report"
  on public.game_compatibility_reports for update
  to authenticated
  using (user_id = auth.uid() and review_status = 'community')
  with check (user_id = auth.uid() and review_status = 'community');

drop policy if exists "Admins review game reports" on public.game_compatibility_reports;
create policy "Admins review game reports"
  on public.game_compatibility_reports for update
  to authenticated
  using (exists (select 1 from public.profiles p where p.id = auth.uid() and p.role::text = 'admin'))
  with check (exists (select 1 from public.profiles p where p.id = auth.uid() and p.role::text = 'admin'));

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('game-evidence', 'game-evidence', false, 157286400, array['image/jpeg','image/png','image/webp','image/gif','video/mp4','video/webm','video/quicktime'])
on conflict (id) do update set
  public = false,
  file_size_limit = 157286400,
  allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "Upload own game evidence" on storage.objects;
create policy "Upload own game evidence"
  on storage.objects for insert to authenticated
  with check (
    bucket_id = 'game-evidence'
    and (storage.foldername(name))[1] = auth.uid()::text
  );

drop policy if exists "Read community game evidence" on storage.objects;
create policy "Read community game evidence"
  on storage.objects for select to authenticated
  using (bucket_id = 'game-evidence');

drop policy if exists "Delete own game evidence" on storage.objects;
create policy "Delete own game evidence"
  on storage.objects for delete to authenticated
  using (
    bucket_id = 'game-evidence'
    and (storage.foldername(name))[1] = auth.uid()::text
  );

-- Admin verification can be managed in Supabase for now; reports are marked verified only after evidence review.
