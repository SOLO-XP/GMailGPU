-- GMailGPU Community Game Lab features
-- Prerequisite: run supabase_game_compatibility.sql first.
-- Safe to run again: tables, columns, policies, triggers and indexes are idempotent.

alter table public.game_compatibility_reports
  add column if not exists test_duration_seconds integer,
  add column if not exists device_temperature_c numeric(5,2),
  add column if not exists evidence_reviewed boolean not null default false,
  add column if not exists evidence_reviewed_at timestamptz,
  add column if not exists evidence_reviewed_by uuid references auth.users(id) on delete set null;

alter table public.game_compatibility_reports
  drop constraint if exists game_compatibility_duration_nonnegative;
alter table public.game_compatibility_reports
  add constraint game_compatibility_duration_nonnegative
  check (test_duration_seconds is null or test_duration_seconds >= 0);

alter table public.game_compatibility_reports
  drop constraint if exists game_compatibility_temperature_reasonable;
alter table public.game_compatibility_reports
  add constraint game_compatibility_temperature_reasonable
  check (device_temperature_c is null or device_temperature_c between 0 and 150);

grant select, insert, update, delete on public.game_compatibility_reports to authenticated;

drop policy if exists "Delete own game compatibility report" on public.game_compatibility_reports;
create policy "Delete own game compatibility report"
  on public.game_compatibility_reports
  for delete
  to authenticated
  using (user_id = auth.uid());

create index if not exists game_compatibility_review_queue_idx
  on public.game_compatibility_reports (review_status, evidence_reviewed, created_at desc);

-- Watchlist: alert users when another matching game report is published.
create table if not exists public.game_watchlist (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  game_name text not null,
  gpu_family text not null default 'Any GPU',
  driver_type text not null default 'Any' check (driver_type in ('Any','System Driver','PanVK','Other')),
  created_at timestamptz not null default now(),
  constraint game_watchlist_name_not_blank check(length(trim(game_name)) > 0)
);

create unique index if not exists game_watchlist_unique_item_idx
  on public.game_watchlist(user_id, lower(game_name), gpu_family, driver_type);

alter table public.game_watchlist enable row level security;
grant select, insert, delete on public.game_watchlist to authenticated;

drop policy if exists "Manage own game watchlist" on public.game_watchlist;
create policy "Manage own game watchlist"
  on public.game_watchlist for all to authenticated
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

create table if not exists public.game_watch_notifications (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  report_id uuid not null references public.game_compatibility_reports(id) on delete cascade,
  game_name text not null,
  message text not null,
  created_at timestamptz not null default now(),
  read_at timestamptz,
  unique(user_id, report_id)
);

alter table public.game_watch_notifications enable row level security;
grant select, update on public.game_watch_notifications to authenticated;

drop policy if exists "Read own game watch notifications" on public.game_watch_notifications;
create policy "Read own game watch notifications"
  on public.game_watch_notifications for select to authenticated
  using (user_id = auth.uid());

drop policy if exists "Mark own game watch notifications read" on public.game_watch_notifications;
create policy "Mark own game watch notifications read"
  on public.game_watch_notifications for update to authenticated
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

create or replace function public.notify_game_watchers()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $function$
begin
  insert into public.game_watch_notifications(user_id, report_id, game_name, message)
  select distinct w.user_id, new.id, new.game_name,
    'A new community test was added for ' || new.game_name ||
    ' (' || new.gpu_family || ' • ' || new.driver_type || ').'
  from public.game_watchlist w
  where lower(trim(w.game_name)) = lower(trim(new.game_name))
    and (w.gpu_family = 'Any GPU' or w.gpu_family = new.gpu_family)
    and (w.driver_type = 'Any' or w.driver_type = new.driver_type)
    and w.user_id <> new.user_id
  on conflict (user_id, report_id) do nothing;

  return new;
end;
$function$;

drop trigger if exists notify_game_watchers_after_insert
  on public.game_compatibility_reports;
create trigger notify_game_watchers_after_insert
  after insert on public.game_compatibility_reports
  for each row execute function public.notify_game_watchers();

-- Prevent a normal user from self-verifying a result or changing review metadata.
create or replace function public.protect_game_report_review_fields()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $function$
begin
  if not exists (
    select 1 from public.profiles p
    where p.id = auth.uid() and p.role::text = 'admin'
  ) then
    new.review_status := old.review_status;
    new.evidence_reviewed := old.evidence_reviewed;
    new.evidence_reviewed_at := old.evidence_reviewed_at;
    new.evidence_reviewed_by := old.evidence_reviewed_by;
    new.user_id := old.user_id;
    new.username := old.username;
    new.evidence_paths := old.evidence_paths;
  else
    if new.review_status = 'verified' and not new.evidence_reviewed then
      raise exception 'A report must have its evidence reviewed before it can be verified.';
    end if;

    if new.evidence_reviewed and not old.evidence_reviewed then
      new.evidence_reviewed_at := now();
      new.evidence_reviewed_by := auth.uid();
    elsif not new.evidence_reviewed then
      new.evidence_reviewed_at := null;
      new.evidence_reviewed_by := null;
    end if;
  end if;

  return new;
end;
$function$;

drop trigger if exists protect_game_report_review_fields
  on public.game_compatibility_reports;
create trigger protect_game_report_review_fields
  before update on public.game_compatibility_reports
  for each row execute function public.protect_game_report_review_fields();

-- Saved game configurations with optional public sharing.
create table if not exists public.game_saved_configurations (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  report_id uuid references public.game_compatibility_reports(id) on delete set null,
  config_name text not null,
  game_name text not null,
  gpu_family text not null,
  driver_type text not null,
  driver_version text,
  device_model text,
  soc text,
  emulator_name text not null,
  emulator_version text,
  wine_version text,
  dxvk_version text,
  vkd3d_version text,
  box64_version text,
  resolution text,
  graphics_preset text,
  notes text,
  is_public boolean not null default false,
  created_at timestamptz not null default now(),
  constraint game_saved_config_name_not_blank check(length(trim(config_name)) > 0),
  constraint game_saved_config_game_not_blank check(length(trim(game_name)) > 0)
);

create index if not exists game_saved_configs_user_idx
  on public.game_saved_configurations(user_id, created_at desc);

alter table public.game_saved_configurations enable row level security;
grant select, insert, update, delete on public.game_saved_configurations to authenticated;

drop policy if exists "Read own or public saved configurations" on public.game_saved_configurations;
create policy "Read own or public saved configurations"
  on public.game_saved_configurations for select to authenticated
  using (user_id = auth.uid() or is_public = true);

drop policy if exists "Insert own saved configurations" on public.game_saved_configurations;
create policy "Insert own saved configurations"
  on public.game_saved_configurations for insert to authenticated
  with check (user_id = auth.uid());

drop policy if exists "Update own saved configurations" on public.game_saved_configurations;
create policy "Update own saved configurations"
  on public.game_saved_configurations for update to authenticated
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

drop policy if exists "Delete own saved configurations" on public.game_saved_configurations;
create policy "Delete own saved configurations"
  on public.game_saved_configurations for delete to authenticated
  using (user_id = auth.uid());

-- Optional public tester profiles; statistics are calculated from submitted tests.
create table if not exists public.game_tester_profiles (
  user_id uuid primary key references auth.users(id) on delete cascade,
  display_name text not null default 'Mali Tester',
  bio text not null default '',
  is_public boolean not null default false,
  updated_at timestamptz not null default now(),
  constraint game_tester_display_name_not_blank check(length(trim(display_name)) > 0),
  constraint game_tester_bio_length check(length(bio) <= 500)
);

alter table public.game_tester_profiles enable row level security;
grant select, insert, update on public.game_tester_profiles to authenticated;

drop policy if exists "Read public tester profiles or own" on public.game_tester_profiles;
create policy "Read public tester profiles or own"
  on public.game_tester_profiles for select to authenticated
  using (is_public = true or user_id = auth.uid());

drop policy if exists "Create own tester profile" on public.game_tester_profiles;
create policy "Create own tester profile"
  on public.game_tester_profiles for insert to authenticated
  with check (user_id = auth.uid());

drop policy if exists "Update own tester profile" on public.game_tester_profiles;
create policy "Update own tester profile"
  on public.game_tester_profiles for update to authenticated
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

-- Discussion threads attached to individual game reports.
create table if not exists public.game_report_discussions (
  id uuid primary key default gen_random_uuid(),
  report_id uuid not null references public.game_compatibility_reports(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  username text not null default 'community member',
  body text not null,
  created_at timestamptz not null default now(),
  constraint game_report_discussion_body_length check(length(trim(body)) between 1 and 2000)
);

create index if not exists game_report_discussions_report_idx
  on public.game_report_discussions(report_id, created_at);

alter table public.game_report_discussions enable row level security;
grant select, insert on public.game_report_discussions to authenticated;

drop policy if exists "Read discussion for visible game reports" on public.game_report_discussions;
create policy "Read discussion for visible game reports"
  on public.game_report_discussions for select to authenticated
  using (
    exists (
      select 1 from public.game_compatibility_reports r
      where r.id = report_id
        and (r.review_status <> 'rejected' or r.user_id = auth.uid()
          or exists (select 1 from public.profiles p where p.id = auth.uid() and p.role::text = 'admin'))
    )
  );

drop policy if exists "Post discussion on visible game reports" on public.game_report_discussions;
create policy "Post discussion on visible game reports"
  on public.game_report_discussions for insert to authenticated
  with check (
    user_id = auth.uid()
    and exists (
      select 1 from public.game_compatibility_reports r
      where r.id = report_id
        and r.review_status <> 'rejected'
    )
  );

create or replace function public.set_game_discussion_username()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $function$
begin
  select p.username into new.username
  from public.profiles p
  where p.id = auth.uid();
  if new.username is null or length(trim(new.username)) = 0 then
    new.username := 'community member';
  end if;
  return new;
end;
$function$;

drop trigger if exists set_game_discussion_username on public.game_report_discussions;
create trigger set_game_discussion_username
  before insert on public.game_report_discussions
  for each row execute function public.set_game_discussion_username();

-- Restrict evidence reads to the owner, visible game reports, or an admin.
-- The second path segment is the compatibility report UUID: <user UUID>/<report UUID>/<file>.
drop policy if exists "Read community game evidence" on storage.objects;
create policy "Read community game evidence"
  on storage.objects
  for select
  to authenticated
  using (
    bucket_id = 'game-evidence'
    and (
      (storage.foldername(name))[1] = auth.uid()::text
      or exists (
        select 1
        from public.game_compatibility_reports r
        where r.id::text = (storage.foldername(name))[2]
          and (
            r.review_status <> 'rejected'
            or r.user_id = auth.uid()
            or exists (
              select 1
              from public.profiles p
              where p.id = auth.uid() and p.role::text = 'admin'
            )
          )
      )
    )
  );

-- Abuse reports are private to the reporter and authorized admins.
create table if not exists public.game_report_flags (
  id uuid primary key default gen_random_uuid(),
  report_id uuid not null references public.game_compatibility_reports(id) on delete cascade,
  reporter_id uuid not null references auth.users(id) on delete cascade,
  reason text not null check(reason in ('Misleading result','Broken evidence','Spam','Privacy concern','Other')),
  details text not null default '',
  status text not null default 'open' check(status in ('open','reviewing','resolved','dismissed')),
  created_at timestamptz not null default now()
);

alter table public.game_report_flags enable row level security;
grant select, insert, update on public.game_report_flags to authenticated;

drop policy if exists "Reporter or admin reads game flags" on public.game_report_flags;
create policy "Reporter or admin reads game flags"
  on public.game_report_flags for select to authenticated
  using (
    reporter_id = auth.uid()
    or exists(select 1 from public.profiles p where p.id = auth.uid() and p.role::text = 'admin')
  );

drop policy if exists "Insert own game report flag" on public.game_report_flags;
create policy "Insert own game report flag"
  on public.game_report_flags for insert to authenticated
  with check (reporter_id = auth.uid());

drop policy if exists "Admins update game flags" on public.game_report_flags;
create policy "Admins update game flags"
  on public.game_report_flags for update to authenticated
  using (exists(select 1 from public.profiles p where p.id = auth.uid() and p.role::text = 'admin'))
  with check (exists(select 1 from public.profiles p where p.id = auth.uid() and p.role::text = 'admin'));

-- Public report statistics remain aggregate summaries; personal profiles are opt-in.
