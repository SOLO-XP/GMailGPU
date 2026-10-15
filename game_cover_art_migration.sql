-- GMailGPU Community Game Lab: game cover artwork.
-- Existing installations can run this once; it is safe to rerun.
alter table public.game_compatibility_reports
  add column if not exists game_image_path text;

comment on column public.game_compatibility_reports.game_image_path is
  'Private storage path for the game cover image displayed in the square community library card.';
