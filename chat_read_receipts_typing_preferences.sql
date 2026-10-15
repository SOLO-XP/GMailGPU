-- GMailGPU collaboration upgrades: sent/delivered/read states, typing, and per-issue notification preferences.
-- Safe to apply more than once. Run against the existing GMailGPU Supabase project.

begin;

-- Store delivery and read timestamps separately. "Delivered" means the recipient's signed-in
-- app fetched the message from the issue chat; "Read" means the issue chat was actually opened.
alter table public.issue_messages
  add column if not exists is_important boolean not null default false,
  add column if not exists delivered_at timestamptz,
  add column if not exists read_at timestamptz;

grant update (delivered_at, read_at) on public.issue_messages to authenticated;
drop policy if exists "issue chat receipt update receiver" on public.issue_messages;
create policy "issue chat receipt update receiver"
on public.issue_messages for update to authenticated
using (
  sender_id <> auth.uid()
  and exists (
    select 1 from public.issues i
    where i.id = issue_id and (i.user_id = auth.uid() or public.is_admin())
  )
)
with check (
  sender_id <> auth.uid()
  and exists (
    select 1 from public.issues i
    where i.id = issue_id and (i.user_id = auth.uid() or public.is_admin())
  )
);

-- A short-lived row signals "typing…". A trigger owns identity and expiry to prevent spoofing.
create table if not exists public.issue_typing_states (
  issue_id uuid not null references public.issues(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade,
  username text not null default '',
  role text not null default 'user',
  expires_at timestamptz not null default (now() + interval '10 seconds'),
  primary key (issue_id, user_id)
);
alter table public.issue_typing_states enable row level security;
grant select, insert, update, delete on public.issue_typing_states to authenticated;

drop policy if exists "issue typing visible to issue participants" on public.issue_typing_states;
create policy "issue typing visible to issue participants"
on public.issue_typing_states for select to authenticated
using (
  exists (
    select 1 from public.issues i
    where i.id = issue_id and (i.user_id = auth.uid() or public.is_admin())
  )
);

drop policy if exists "issue typing insert own" on public.issue_typing_states;
create policy "issue typing insert own"
on public.issue_typing_states for insert to authenticated
with check (
  user_id = auth.uid()
  and exists (
    select 1 from public.issues i
    where i.id = issue_id and (i.user_id = auth.uid() or public.is_admin())
  )
);

drop policy if exists "issue typing update own" on public.issue_typing_states;
create policy "issue typing update own"
on public.issue_typing_states for update to authenticated
using (
  user_id = auth.uid()
  and exists (
    select 1 from public.issues i
    where i.id = issue_id and (i.user_id = auth.uid() or public.is_admin())
  )
)
with check (
  user_id = auth.uid()
  and exists (
    select 1 from public.issues i
    where i.id = issue_id and (i.user_id = auth.uid() or public.is_admin())
  )
);

drop policy if exists "issue typing delete own" on public.issue_typing_states;
create policy "issue typing delete own"
on public.issue_typing_states for delete to authenticated
using (
  user_id = auth.uid()
  and exists (
    select 1 from public.issues i
    where i.id = issue_id and (i.user_id = auth.uid() or public.is_admin())
  )
);

create or replace function public.set_issue_typing_identity()
returns trigger
language plpgsql
security definer
set search_path = public
as $set_typing$
declare
  profile_name text;
  profile_role text;
begin
  if auth.uid() is null or new.user_id <> auth.uid() then
    raise exception 'Typing state can only be changed by its owner.';
  end if;

  if not exists (
    select 1 from public.issues i
    where i.id = new.issue_id and (i.user_id = auth.uid() or public.is_admin())
  ) then
    raise exception 'Typing state requires issue access.';
  end if;

  select p.username, p.role::text into profile_name, profile_role
  from public.profiles p where p.id = new.user_id;
  if profile_name is null then raise exception 'Profile not found.'; end if;

  new.username := profile_name;
  new.role := profile_role;
  new.expires_at := now() + interval '10 seconds';
  return new;
end;
$set_typing$;

drop trigger if exists set_issue_typing_identity_trigger on public.issue_typing_states;
create trigger set_issue_typing_identity_trigger
before insert or update on public.issue_typing_states
for each row execute procedure public.set_issue_typing_identity();

create index if not exists issue_typing_expiry_idx
on public.issue_typing_states (issue_id, expires_at);

-- Preferences apply to one account and one issue. Modes: all replies, important-only, or muted.
create table if not exists public.issue_notification_preferences (
  user_id uuid not null references public.profiles(id) on delete cascade,
  issue_id uuid not null references public.issues(id) on delete cascade,
  mode text not null default 'all' check (mode in ('all', 'important', 'muted')),
  updated_at timestamptz not null default now(),
  primary key (user_id, issue_id)
);
alter table public.issue_notification_preferences enable row level security;
grant select, insert, update, delete on public.issue_notification_preferences to authenticated;

drop policy if exists "issue notification preference own read" on public.issue_notification_preferences;
create policy "issue notification preference own read"
on public.issue_notification_preferences for select to authenticated
using (
  user_id = auth.uid()
  and exists (
    select 1 from public.issues i
    where i.id = issue_id and (i.user_id = auth.uid() or public.is_admin())
  )
);

drop policy if exists "issue notification preference own insert" on public.issue_notification_preferences;
create policy "issue notification preference own insert"
on public.issue_notification_preferences for insert to authenticated
with check (
  user_id = auth.uid()
  and exists (
    select 1 from public.issues i
    where i.id = issue_id and (i.user_id = auth.uid() or public.is_admin())
  )
);

drop policy if exists "issue notification preference own update" on public.issue_notification_preferences;
create policy "issue notification preference own update"
on public.issue_notification_preferences for update to authenticated
using (
  user_id = auth.uid()
  and exists (
    select 1 from public.issues i
    where i.id = issue_id and (i.user_id = auth.uid() or public.is_admin())
  )
)
with check (
  user_id = auth.uid()
  and exists (
    select 1 from public.issues i
    where i.id = issue_id and (i.user_id = auth.uid() or public.is_admin())
  )
);

drop policy if exists "issue notification preference own delete" on public.issue_notification_preferences;
create policy "issue notification preference own delete"
on public.issue_notification_preferences for delete to authenticated
using (
  user_id = auth.uid()
  and exists (
    select 1 from public.issues i
    where i.id = issue_id and (i.user_id = auth.uid() or public.is_admin())
  )
);

-- Honour each recipient's per-issue preference before creating an in-app alert.
create or replace function public.notify_issue_chat_message()
returns trigger
language plpgsql
security definer
set search_path = public
as $notify_chat$
declare
  target_user uuid;
  issue_number_value bigint;
  issue_title text;
  sender_role text;
  sender_name text;
  preview text;
  recipient_mode text;
  alert_title text;
begin
  select i.user_id, i.issue_number, i.title
  into target_user, issue_number_value, issue_title
  from public.issues i where i.id = new.issue_id;

  if target_user is null then return new; end if;

  select p.role::text, p.username into sender_role, sender_name
  from public.profiles p where p.id = new.sender_id;

  preview := left(regexp_replace(coalesce(new.body, ''), E'[\n\r\t]+', ' ', 'g'), 150);
  alert_title := (case when new.is_important then 'Important ' else '' end)
    || (case when sender_role = 'admin' then 'Developer reply' else 'User reply' end)
    || ' • Issue #' || issue_number_value;

  if sender_role = 'admin' then
    if target_user <> new.sender_id then
      select p.mode into recipient_mode
      from public.issue_notification_preferences p
      where p.user_id = target_user and p.issue_id = new.issue_id;
      recipient_mode := coalesce(recipient_mode, 'all');
      if recipient_mode = 'muted' or (recipient_mode = 'important' and not new.is_important) then
        return new;
      end if;

      insert into public.notifications(user_id, issue_id, title, message)
      values (
        target_user, new.issue_id, alert_title,
        coalesce(sender_name, 'Developer') || ' replied to "' || issue_title || '": ' || preview
      );
    end if;
  else
    insert into public.notifications(user_id, issue_id, title, message)
    select p.id, new.issue_id, alert_title,
      coalesce(sender_name, 'User') || ' replied to "' || issue_title || '": ' || preview
    from public.profiles p
    left join public.issue_notification_preferences pref
      on pref.user_id = p.id and pref.issue_id = new.issue_id
    where p.role::text = 'admin'
      and p.id <> new.sender_id
      and coalesce(pref.mode, 'all') <> 'muted'
      and (coalesce(pref.mode, 'all') <> 'important' or new.is_important);
  end if;

  return new;
end;
$notify_chat$;

drop trigger if exists issue_chat_message_notification on public.issue_messages;
create trigger issue_chat_message_notification
after insert on public.issue_messages
for each row execute procedure public.notify_issue_chat_message();

commit;
