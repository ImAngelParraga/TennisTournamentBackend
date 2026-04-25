ALTER TABLE user_trainings
    ADD COLUMN IF NOT EXISTS visibility VARCHAR(16);

UPDATE user_trainings
SET visibility = 'PRIVATE'
WHERE visibility IS NULL;

ALTER TABLE user_trainings
    ALTER COLUMN visibility SET DEFAULT 'PRIVATE';

ALTER TABLE user_trainings
    ALTER COLUMN visibility SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'user_trainings_visibility_valid'
    ) THEN
        ALTER TABLE user_trainings
            ADD CONSTRAINT user_trainings_visibility_valid
                CHECK (visibility IN ('PUBLIC', 'PRIVATE'));
    END IF;
END $$;
