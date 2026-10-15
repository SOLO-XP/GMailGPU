-- GMailGPU: automatic closure for issues without developer review.
-- Run this once in Supabase SQL Editor after deploying the matching app update.
-- Requires the Supabase Cron (pg_cron) extension to be enabled.

alter table public.issues
  add column if not exists last_admin_viewed_at timestamptz;

create index if not exists issues_last_admin_viewed_at_idx
  on public.issues (last_admin_viewed_at);

-- Permit an owner to reopen only their own closed issue; block changes to all
-- other issue fields during that owner-initiated update.
create or replace function public.guard_owner_issue_reopen()
returns trigger
language plpgsql
set search_path = public
as $
begin
  -- Scheduled SQL runs without an auth.uid(); allow only trusted database roles.
  if auth.uid() is null then
    if current_user in ('postgres', 'service_role', 'supabase_admin') then
      return new;
    end if;
    raise exception 'A signed-in owner or administrator is required.';
  end if;

  if public.is_admin() then
    return new;
  end if;

  if old.user_id is distinct from auth.uid()
     or old.status <> 'closed'
     or new.status <> 'open'
     or (to_jsonb(new) - 'status') is distinct from (to_jsonb(old) - 'status') then
    raise exception 'Users may only reopen their own closed issue.';
  end if;

  return new;
end;
$$;

drop trigger if exists guard_owner_issue_reopen_trigger on public.issues;
create trigger guard_owner_issue_reopen_trigger
before update on public.issues
for each row execute procedure public.guard_owner_issue_reopen();

drop policy if exists "issue owner reopen closed" on public.issues;
create policy "issue owner reopen closed"
on public.issues for update
using (user_id = auth.uid() and status = 'closed')
with check (user_id = auth.uid() and status = 'open');

-- Close issues that have not been opened by any admin for 14 days.
-- For never-reviewed issues, use created_at as the starting point.
create or replace function public.auto_close_unattended_issues()
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  closed_count integer;
begin
  update public.issues
  set status = 'closed',
      developer_note = 'Closed automatically: No developer was available to investigate your issue within 14 days. Please reopen it if you still need help.'
  where status in ('open', 'investigating', 'fix_in_progress', 'testing')
    and coalesce(last_admin_viewed_at, created_at) <= now() - interval '14 days';

  get diagnostics closed_count = row_count;
  return closed_count;
end;
$;

-- Only the trusted cron/database owner may execute the global auto-close function.
revoke all on function public.auto_close_unattended_issues() from public, anon, authenticated;

-- Give the owner a useful notification explaining automatic closure.
create or replace function public.notify_issue_resolved()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if (new.status in ('fixed','closed')) and (old.status not in ('fixed','closed')) then
    if new.status = 'closed' and new.developer_note like 'Closed automatically:%' then
      insert into public.notifications (user_id, issue_id, title, message)
      values (
        new.user_id,
        new.id,
        'Issue #' || new.issue_number || ' closed automatically',
        new.developer_note || ' Open GMailGPU to reopen it.'
      );
    else
      insert into public.notifications (user_id, issue_id, title, message)
      values (
        new.user_id,
        new.id,
        'Issue #' || new.issue_number || ' resolved',
        'The developer marked your "' || new.title || '" issue as resolved. Open GMailGPU to see the final status and developer note.'
      );
    end if;
  end if;
  return new;
end;
$$;

-- Enable pg_cron in Supabase first if it is not already enabled.
create extension if not exists pg_cron with schema pg_catalog;

-- A repeated schedule call with the same job name replaces the existing job.
select cron.schedule(
  'gmailgpu-auto-close-unattended-issues',
  '0 * * * *',
  'select public.auto_close_unattended_issues();'
);
