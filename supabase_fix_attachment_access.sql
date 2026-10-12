-- GMailGPU attachment-access hotfix
-- Run this once in Supabase Dashboard -> SQL Editor -> New query.
-- Keeps issue-files private while allowing only the issue owner and admins to read/download files.

grant usage on schema public to authenticated;
grant select on public.issues to authenticated;
grant select, insert, delete on public.attachments to authenticated;

drop policy if exists "attachment read" on public.attachments;
create policy "attachment read" on public.attachments
for select to authenticated
using (
  exists (
    select 1
    from public.issues i
    where i.id = attachments.issue_id
      and (i.user_id = auth.uid() or public.is_admin())
  )
);

drop policy if exists "attachment insert" on public.attachments;
create policy "attachment insert" on public.attachments
for insert to authenticated
with check (
  exists (
    select 1
    from public.issues i
    where i.id = attachments.issue_id
      and i.user_id = auth.uid()
  )
);

drop policy if exists "attachment delete" on public.attachments;
create policy "attachment delete" on public.attachments
for delete to authenticated
using (
  exists (
    select 1
    from public.issues i
    where i.id = attachments.issue_id
      and (i.user_id = auth.uid() or public.is_admin())
  )
);

insert into storage.buckets (id, name, public)
values ('issue-files', 'issue-files', false)
on conflict (id) do update set public = false;

drop policy if exists "issue files read" on storage.objects;
create policy "issue files read" on storage.objects
for select to authenticated
using (
  bucket_id = 'issue-files'
  and exists (
    select 1
    from public.issues i
    where i.id::text = split_part(storage.objects.name, '/', 1)
      and (i.user_id = auth.uid() or public.is_admin())
  )
);

drop policy if exists "issue files insert" on storage.objects;
create policy "issue files insert" on storage.objects
for insert to authenticated
with check (
  bucket_id = 'issue-files'
  and exists (
    select 1
    from public.issues i
    where i.id::text = split_part(storage.objects.name, '/', 1)
      and i.user_id = auth.uid()
  )
);

drop policy if exists "issue files delete" on storage.objects;
create policy "issue files delete" on storage.objects
for delete to authenticated
using (
  bucket_id = 'issue-files'
  and exists (
    select 1
    from public.issues i
    where i.id::text = split_part(storage.objects.name, '/', 1)
      and (i.user_id = auth.uid() or public.is_admin())
  )
);
