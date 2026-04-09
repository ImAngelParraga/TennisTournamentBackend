CREATE TABLE IF NOT EXISTS rackets (
    id SERIAL PRIMARY KEY,
    public_token UUID NOT NULL UNIQUE,
    display_name VARCHAR(255) NOT NULL,
    brand VARCHAR(255),
    model VARCHAR(255),
    string_pattern VARCHAR(32),
    owner_user_id INTEGER REFERENCES users(id) ON DELETE SET NULL,
    owner_name VARCHAR(255),
    created_by_user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS racket_stringings (
    id SERIAL PRIMARY KEY,
    racket_id INTEGER NOT NULL REFERENCES rackets(id) ON DELETE CASCADE,
    stringing_date DATE NOT NULL,
    mains_kg NUMERIC(6, 2) NOT NULL,
    crosses_kg NUMERIC(6, 2) NOT NULL,
    mains_lb NUMERIC(6, 2) NOT NULL,
    crosses_lb NUMERIC(6, 2) NOT NULL,
    main_string_brand VARCHAR(255),
    main_string_model VARCHAR(255),
    main_string_gauge VARCHAR(64),
    cross_string_brand VARCHAR(255),
    cross_string_model VARCHAR(255),
    cross_string_gauge VARCHAR(64),
    notes TEXT,
    created_by_user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    updated_by_user_id INTEGER REFERENCES users(id) ON DELETE SET NULL,
    deleted_by_user_id INTEGER REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT racket_stringings_mains_kg_positive_check CHECK (mains_kg > 0),
    CONSTRAINT racket_stringings_crosses_kg_positive_check CHECK (crosses_kg > 0),
    CONSTRAINT racket_stringings_mains_lb_positive_check CHECK (mains_lb > 0),
    CONSTRAINT racket_stringings_crosses_lb_positive_check CHECK (crosses_lb > 0)
);

CREATE INDEX IF NOT EXISTS racket_stringings_racket_id_stringing_date_idx
    ON racket_stringings (racket_id, stringing_date DESC, created_at DESC);

CREATE TABLE IF NOT EXISTS racket_stringing_audits (
    id SERIAL PRIMARY KEY,
    racket_id INTEGER NOT NULL REFERENCES rackets(id) ON DELETE CASCADE,
    racket_stringing_id INTEGER NOT NULL REFERENCES racket_stringings(id) ON DELETE CASCADE,
    action VARCHAR(16) NOT NULL,
    actor_user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    snapshot_json TEXT NOT NULL,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT racket_stringing_audits_action_check CHECK (action IN ('CREATED', 'UPDATED', 'DELETED'))
);

CREATE INDEX IF NOT EXISTS racket_stringing_audits_racket_id_idx
    ON racket_stringing_audits (racket_id, occurred_at DESC);
