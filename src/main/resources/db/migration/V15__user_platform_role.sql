-- Platform-level role. Clubs are provisioned manually by the platform operator:
-- only PLATFORM_ADMIN may create/delete clubs. The role is assigned via manual SQL,
-- never via API, e.g.:
--   UPDATE users SET role = 'PLATFORM_ADMIN' WHERE auth_subject = '<clerk user sub>';
-- (The operator must have signed in at least once so their users row exists.)
ALTER TABLE users ADD COLUMN IF NOT EXISTS role VARCHAR(32) NOT NULL DEFAULT 'USER';

ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (role IN ('USER', 'PLATFORM_ADMIN'));
