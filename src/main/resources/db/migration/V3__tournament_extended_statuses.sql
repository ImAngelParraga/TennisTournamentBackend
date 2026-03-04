ALTER TABLE tournaments
    DROP CONSTRAINT IF EXISTS tournaments_status_check;

ALTER TABLE tournaments
    ADD CONSTRAINT tournaments_status_check
    CHECK (status IN ('DRAFT', 'STARTED', 'COMPLETED', 'CANCELLED', 'ABANDONED'));
