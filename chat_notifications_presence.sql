-- GMailGPU chat notifications, device push tokens, and online presence.
-- Run once in the existing project SQL editor. Safe to rerun.
-- Requires the existing public.issues, public.profiles and public.notifications tables.
-- Creates public.issue_messages if the issue-chat table does not yet exist.

begin;

-- Ensure issue chat exists on installations that only ran the base schema.
create table if not exists public.issue_messages (
  id uuid primary key default gen_random_uuid(),
  issue_id uuid not null references public.issues(id) on delete cascade,
  sender_id uuid not null references public.profiles(id) on delete cascade,
  body text not null check (length(trim(body)) between 1 and 4000),
  created_at timestamptz not null default now()
);
create index if not exists issue_messages_issue_created_idx
  on public.issue_messages(issue_id, created_at);

alter table public.issue_messages enable row level security;
grant select, insert on public.issue_messages to authenticated;

drop policy if exists "issue chat read owner admin" on public.issue_messages;
create policy "issue chat read owner admin"
on public.issue_messages for select to authenticated
using (
  exists (
    select 1 from public.issues i
    where i.id = issue_id and (i.user_id = auth.uid() or public.is_admin())
  )
);

drop policy if exists "issue chat insert owner admin" on public.issue_messages;
create policy "issue chat insert owner admin"
on public.issue_messages for insert to authenticated
with check (
  sender_id = auth.uid() and exists (
    select 1 from public.issues i
    where i.id = issue_id and (i.user_id = auth.uid() or public.is_admin())
  )
);

-- Live/last-seen presence. A client refreshes last_seen_at while the app is foregrounded.
create table if not exists public.user_presence (
  user_id uuid primary key references public.profiles(id) on delete cascade,
  username text not null default '',
  role text not null default 'user',
  is_online boolean not null default false,
  last_seen_at timestamptz not null default now()
);
alter table public.user_presence enable row level security;
grant select, insert, update on public.user_presence to authenticated;

drop policy if exists "presence visible to owner admins and admin team" on public.user_presence;
create policy "presence visible to owner admins and admin team"
on public.user_presence for select to authenticated
using (user_id = auth.uid() or public.is_admin() or role = 'admin');

drop policy if exists "presence own insert" on public.user_presence;
create policy "presence own insert"
on public.user_presence for insert to authenticated
with check (user_id = auth.uid());

drop policy if exists "presence own update" on public.user_presence;
create policy "presence own update"
on public.user_presence for update to authenticated
using (user_id = auth.uid())
with check (user_id = auth.uid());

create or replace function public.set_presence_identity()
returns trigger language plpgsql security definer set search_path = public
as $presence_identity$
declare profile_name text; profile_role text;
begin
  if auth.uid() is null or new.user_id <> auth.uid() then
    raise exception 'Presence can only be changed by its owner.';
  end if;
  select p.username, p.role::text into profile_name, profile_role
  from public.profiles p where p.id = new.user_id;
  if profile_name is null then raise exception 'Profile not found.'; end if;
  new.username := profile_name;
  new.role := profile_role;
  new.last_seen_at := now();
  return new;
end;
$presence_identity$;

drop trigger if exists set_presence_identity_trigger on public.user_presence;
create trigger set_presence_identity_trigger
before insert or update on public.user_presence
for each row execute procedure public.set_presence_identity();

create index if not exists user_presence_online_idx
on public.user_presence (role, is_online, last_seen_at desc);

-- FCM tokens stay private to the signed-in account that registered them.
create table if not exists public.device_push_tokens (
  token text primary key,
  user_id uuid not null references public.profiles(id) on delete cascade,
  platform text not null default 'android',
  updated_at timestamptz not null default now()
);
alter table public.device_push_tokens enable row level security;
grant select, insert, update, delete on public.device_push_tokens to authenticated;

drop policy if exists "push token own read" on public.device_push_tokens;
create policy "push token own read" on public.device_push_tokens
for select to authenticated using (user_id = auth.uid());

drop policy if exists "push token own insert" on public.device_push_tokens;
create policy "push token own insert" on public.device_push_tokens
for insert to authenticated with check (user_id = auth.uid());

drop policy if exists "push token own update" on public.device_push_tokens;
create policy "push token own update" on public.device_push_tokens
for update to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());

drop policy if exists "push token own delete" on public.device_push_tokens;
create policy "push token own delete" on public.device_push_tokens
for delete to authenticated using (user_id = auth.uid());

-- Every new chat message creates an inbox notification for its recipient(s).
-- The message itself remains readable only under the existing issue-message RLS rules.
create or replace function public.notify_issue_chat_message()
returns trigger language plpgsql security definer set search_path = public
as $notify_chat$
declare
  target_user uuid;
  issue_number_value bigint;
  issue_title text;
  sender_role text;
  sender_name text;
  preview text;
begin
  select i.user_id, i.issue_number, i.title
  into target_user, issue_number_value, issue_title
  from public.issues i where i.id = new.issue_id;

  if target_user is null then return new; end if;

  select p.role::text, p.username into sender_role, sender_name
  from public.profiles p where p.id = new.sender_id;

  preview := left(regexp_replace(coalesce(new.body, ''), E'[\n\r\t]+', ' ', 'g'), 150);

  if sender_role = 'admin' then
    if target_user <> new.sender_id then
      insert into public.notifications(user_id, issue_id, title, message)
      values (
        target_user,
        new.issue_id,
        'New developer reply • Issue #' || issue_number_value,
        coalesce(sender_name, 'Developer') || ' replied to "' || issue_title || '": ' || preview
      );
    end if;
  else
    insert into public.notifications(user_id, issue_id, title, message)
    select p.id, new.issue_id,
      'New user reply • Issue #' || issue_number_value,
      coalesce(sender_name, 'User') || ' replied to "' || issue_title || '": ' || preview
    from public.profiles p
    where p.role::text = 'admin' and p.id <> new.sender_id;
  end if;

  return new;
end;
$notify_chat$;

drop trigger if exists issue_chat_message_notification on public.issue_messages;
create trigger issue_chat_message_notification
after insert on public.issue_messages
for each row execute procedure public.notify_issue_chat_message();

commit;
