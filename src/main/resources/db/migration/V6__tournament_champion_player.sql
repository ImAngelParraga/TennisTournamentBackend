ALTER TABLE tournaments
    ADD COLUMN IF NOT EXISTS champion_player_id INTEGER REFERENCES players(id);
