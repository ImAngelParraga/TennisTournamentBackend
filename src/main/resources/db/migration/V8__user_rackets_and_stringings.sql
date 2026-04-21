CREATE TABLE IF NOT EXISTS rackets (
    id SERIAL PRIMARY KEY,
    owner_user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    display_name VARCHAR(255) NOT NULL,
    brand VARCHAR(255),
    model VARCHAR(255),
    string_pattern VARCHAR(64),
    visibility VARCHAR(16) NOT NULL DEFAULT 'PRIVATE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT rackets_visibility_check CHECK (visibility IN ('PUBLIC', 'PRIVATE'))
);

CREATE INDEX IF NOT EXISTS rackets_owner_user_id_idx
    ON rackets (owner_user_id, deleted_at);

CREATE INDEX IF NOT EXISTS rackets_owner_visibility_idx
    ON rackets (owner_user_id, visibility, deleted_at);

CREATE TABLE IF NOT EXISTS racket_stringings (
    id SERIAL PRIMARY KEY,
    racket_id INTEGER NOT NULL REFERENCES rackets(id) ON DELETE RESTRICT,
    stringing_date DATE NOT NULL,
    mains_tension_kg NUMERIC(6, 2) NOT NULL,
    crosses_tension_kg NUMERIC(6, 2) NOT NULL,
    main_string_type VARCHAR(255),
    cross_string_type VARCHAR(255),
    performance_notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT racket_stringings_mains_tension_positive_check CHECK (mains_tension_kg > 0),
    CONSTRAINT racket_stringings_crosses_tension_positive_check CHECK (crosses_tension_kg > 0)
);

CREATE INDEX IF NOT EXISTS racket_stringings_racket_history_idx
    ON racket_stringings (racket_id, deleted_at, stringing_date DESC, created_at DESC);
