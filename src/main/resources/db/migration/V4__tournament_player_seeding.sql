ALTER TABLE tournament_players
    ADD COLUMN IF NOT EXISTS seed INTEGER;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'tournament_players_seed_positive_check'
    ) THEN
        ALTER TABLE tournament_players
            ADD CONSTRAINT tournament_players_seed_positive_check
            CHECK (seed IS NULL OR seed > 0);
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS tournament_players_tournament_id_seed_uidx
    ON tournament_players (tournament_id, seed)
    WHERE seed IS NOT NULL;
