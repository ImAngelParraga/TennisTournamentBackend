-- Profile fields projected from Clerk (synced via webhook / self-edit).
ALTER TABLE users ADD COLUMN IF NOT EXISTS name VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS image_url VARCHAR(1024);
