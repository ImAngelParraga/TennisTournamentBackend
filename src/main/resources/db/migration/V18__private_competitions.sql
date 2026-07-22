ALTER TABLE tournaments ALTER COLUMN club_id DROP NOT NULL;

ALTER TABLE tournaments
    ADD COLUMN owner_user_id INTEGER REFERENCES users(id),
    ADD COLUMN visibility VARCHAR(10) NOT NULL DEFAULT 'PUBLIC',
    ADD COLUMN invite_code VARCHAR(8);

ALTER TABLE tournaments
    ADD CONSTRAINT tournaments_visibility_check CHECK (visibility IN ('PUBLIC', 'PRIVATE'));

ALTER TABLE tournaments
    ADD CONSTRAINT tournaments_owner_xor_check CHECK (
        (club_id IS NOT NULL AND owner_user_id IS NULL AND visibility = 'PUBLIC')
        OR
        (club_id IS NULL AND owner_user_id IS NOT NULL AND visibility = 'PRIVATE' AND invite_code IS NOT NULL)
    );

CREATE UNIQUE INDEX tournaments_invite_code_unique
    ON tournaments(invite_code)
    WHERE invite_code IS NOT NULL;

CREATE TABLE leagues (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    owner_user_id INTEGER NOT NULL REFERENCES users(id),
    invite_code VARCHAR(8) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE league_members (
    league_id INTEGER NOT NULL REFERENCES leagues(id) ON DELETE CASCADE,
    player_id INTEGER NOT NULL REFERENCES players(id),
    rating INTEGER NOT NULL DEFAULT 1000,
    rated_matches INTEGER NOT NULL DEFAULT 0,
    wins INTEGER NOT NULL DEFAULT 0,
    losses INTEGER NOT NULL DEFAULT 0,
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (league_id, player_id),
    CHECK (rating >= 800),
    CHECK (rated_matches >= 0),
    CHECK (wins >= 0),
    CHECK (losses >= 0)
);

CREATE TABLE league_matches (
    id SERIAL PRIMARY KEY,
    league_id INTEGER NOT NULL REFERENCES leagues(id) ON DELETE CASCADE,
    player1_id INTEGER NOT NULL REFERENCES players(id),
    player2_id INTEGER NOT NULL REFERENCES players(id),
    winner_id INTEGER NOT NULL REFERENCES players(id),
    score JSONB,
    played_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by_user_id INTEGER NOT NULL REFERENCES users(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CHECK (player1_id <> player2_id),
    CHECK (winner_id = player1_id OR winner_id = player2_id)
);

CREATE INDEX league_matches_league_played_at_idx ON league_matches(league_id, played_at, id);

CREATE TABLE league_rating_events (
    id SERIAL PRIMARY KEY,
    league_id INTEGER NOT NULL REFERENCES leagues(id) ON DELETE CASCADE,
    league_match_id INTEGER REFERENCES league_matches(id) ON DELETE CASCADE,
    player_id INTEGER NOT NULL REFERENCES players(id),
    reason VARCHAR(20) NOT NULL CHECK (reason = 'MATCH'),
    delta INTEGER NOT NULL,
    rating_after INTEGER NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX league_rating_events_league_player_created_idx
    ON league_rating_events(league_id, player_id, created_at);
