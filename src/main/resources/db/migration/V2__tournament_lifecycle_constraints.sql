ALTER TABLE tournaments
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'DRAFT';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'tournaments_status_check'
    ) THEN
        ALTER TABLE tournaments
            ADD CONSTRAINT tournaments_status_check
            CHECK (status IN ('DRAFT', 'STARTED', 'COMPLETED'));
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS tournament_phases_tournament_id_phase_order_idx
    ON tournament_phases (tournament_id, phase_order);
