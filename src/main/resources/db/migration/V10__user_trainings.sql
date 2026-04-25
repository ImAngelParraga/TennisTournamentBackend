CREATE TABLE IF NOT EXISTS user_trainings (
    id SERIAL PRIMARY KEY,
    owner_user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    training_date DATE NOT NULL,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS user_trainings_owner_month_idx
    ON user_trainings (owner_user_id, training_date DESC, created_at DESC);
