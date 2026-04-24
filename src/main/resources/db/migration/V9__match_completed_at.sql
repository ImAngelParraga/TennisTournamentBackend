ALTER TABLE matches ADD COLUMN completed_at TIMESTAMP;

UPDATE matches
SET completed_at = COALESCE(updated_at, created_at)
WHERE status IN ('COMPLETED', 'WALKOVER')
  AND completed_at IS NULL;

CREATE INDEX idx_matches_completed_at ON matches(completed_at);
CREATE INDEX idx_matches_player1_id ON matches(player1_id);
CREATE INDEX idx_matches_player2_id ON matches(player2_id);
