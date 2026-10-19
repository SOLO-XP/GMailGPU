-- GMailGPU profile photos and per-device issue defaults support.
-- Safe to rerun. Run once in Supabase SQL Editor before testing profile photo uploads.

alter table public.profiles add column if not exists avatar_url text;

-- Users may update only the avatar_url column; they cannot change username or role through this grant.
grant update (avatar_url) on public.profiles to authenticated;

drop policy if exists "profile own avatar update" on public.profiles;
create policy "profile own avatar update"
on public.profiles for update to authenticated
using (id = (select auth.uid()))
with check (id = (select auth.uid()));

-- Allow issue owners to see admin profile names/photos in their issue chat.
-- Admins retain access to all profiles; regular users otherwise see only their own profile.
drop policy if exists "profile chat counterpart read" on public.profiles;
create policy "profile chat counterpart read"
on public.profiles for select to authenticated
using (
  id = (select auth.uid())
  or public.is_admin()
  or (
    role = 'admin'
    and exists (
      select 1 from public.issues i
      where i.user_id = (select auth.uid())
    )
  )
);

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'profile-avatars',
  'profile-avatars',
  true,
  8388608,
  array['image/jpeg','image/png','image/webp','image/gif']
)
on conflict (id) do update
set public = true,
    file_size_limit = 8388608,
    allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "Profile avatars are publicly readable" on storage.objects;
create policy "Profile avatars are publicly readable"
on storage.objects for select to public
using (bucket_id = 'profile-avatars');

drop policy if exists "Users upload their own profile avatar" on storage.objects;
create policy "Users upload their own profile avatar"
on storage.objects for insert to authenticated
with check (
  bucket_id = 'profile-avatars'
  and (storage.foldername(name))[1] = (select auth.uid())::text
);

drop policy if exists "Users update their own profile avatar" on storage.objects;
create policy "Users update their own profile avatar"
on storage.objects for update to authenticated
using (
  bucket_id = 'profile-avatars'
  and (storage.foldername(name))[1] = (select auth.uid())::text
)
with check (
  bucket_id = 'profile-avatars'
  and (storage.foldername(name))[1] = (select auth.uid())::text
);

drop policy if exists "Users delete their own profile avatar" on storage.objects;
create policy "Users delete their own profile avatar"
on storage.objects for delete to authenticated
using (
  bucket_id = 'profile-avatars'
  and (storage.foldername(name))[1] = (select auth.uid())::text
);
