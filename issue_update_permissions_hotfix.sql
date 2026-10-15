-- GMailGPU issue-update permission hotfix.
-- Safe to rerun. This grants table privileges only to signed-in users.
-- Row-level security still limits issue updates to administrators, except that
-- an issue owner may reopen their own closed issue using the dedicated policy.
begin;

alter table public.issues enable row level security;

grant usage on schema public to authenticated;
grant select, update on table public.issues to authenticated;
grant execute on function public.is_admin(uuid) to authenticated;

drop policy if exists "issue admin update" on public.issues;
create policy "issue admin update"
on public.issues
for update
to authenticated
using (public.is_admin())
with check (public.is_admin());

drop policy if exists "issue owner reopen closed" on public.issues;
create policy "issue owner reopen closed"
on public.issues
for update
to authenticated
using (user_id = auth.uid() and status = 'closed')
with check (user_id = auth.uid() and status = 'open');

commit;
