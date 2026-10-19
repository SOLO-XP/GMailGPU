-- Adds the optional game cover image path expected by GameCompatibilityScreen.kt.
-- Safe to run more than once; existing community reports are preserved.
ALTER TABLE public.game_compatibility_reports
    ADD COLUMN IF NOT EXISTS game_image_path text;

COMMENT ON COLUMN public.game_compatibility_reports.game_image_path IS
    'Optional URL or storage path for the game cover image attached to a community compatibility report.';

-- Refresh PostgREST's schema cache so inserts including game_image_path work immediately.
NOTIFY pgrst, 'reload schema';
