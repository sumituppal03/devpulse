-- V8__add_standup_finalize_and_edit_tracking.sql
--
-- Adds two columns that support the standup finalize/edit flow:
-- final_content: the developer's edited version (NULL until finalized)
-- edit_distance: how much the developer changed the AI draft (product quality metric)
--
-- These are nullable because existing standups were never finalized via the API —
-- retroactively requiring values would break the migration.

ALTER TABLE standups
    ADD COLUMN IF NOT EXISTS final_content TEXT,
    ADD COLUMN IF NOT EXISTS edit_distance INTEGER;
