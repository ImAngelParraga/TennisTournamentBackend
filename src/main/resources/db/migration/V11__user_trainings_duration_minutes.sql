ALTER TABLE user_trainings
    ADD COLUMN IF NOT EXISTS duration_minutes INTEGER;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'user_trainings_duration_minutes_positive'
    ) THEN
        ALTER TABLE user_trainings
            ADD CONSTRAINT user_trainings_duration_minutes_positive
                CHECK (duration_minutes IS NULL OR duration_minutes > 0);
    END IF;
END $$;
