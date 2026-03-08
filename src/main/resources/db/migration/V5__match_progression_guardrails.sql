ALTER TABLE matches
    ADD COLUMN IF NOT EXISTS round_slot INTEGER;

WITH ranked_matches AS (
    SELECT
        id,
        ROW_NUMBER() OVER (PARTITION BY phase_id, round ORDER BY id) AS computed_round_slot
    FROM matches
)
UPDATE matches m
SET round_slot = ranked_matches.computed_round_slot
FROM ranked_matches
WHERE m.id = ranked_matches.id
  AND m.round_slot IS NULL;

ALTER TABLE matches
    ALTER COLUMN round_slot SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'matches_round_slot_positive_check'
    ) THEN
        ALTER TABLE matches
            ADD CONSTRAINT matches_round_slot_positive_check
            CHECK (round_slot > 0);
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS matches_phase_round_round_slot_uidx
    ON matches (phase_id, round, round_slot);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'match_dependencies_no_self_reference_check'
    ) THEN
        ALTER TABLE match_dependencies
            ADD CONSTRAINT match_dependencies_no_self_reference_check
            CHECK (match_id <> required_match_id);
    END IF;
END $$;
