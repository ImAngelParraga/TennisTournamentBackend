# Database Migrations (Flyway + Supabase)

This project uses Flyway migrations as the source of truth for schema changes.

## Why this setup

- Supabase project/infra is created manually.
- Database schema is created by migration files in this repo.
- App runtime schema mutation should remain disabled in hosted environments.

## Migration files location

- `src/main/resources/db/migration`
- Baseline migration: `V1__baseline.sql`

## Environment variables

Application DB connection:
- `DATABASE_URL`
- `DATABASE_DRIVER`
- `DATABASE_USER`
- `DATABASE_PASSWORD`
- `DATABASE_AUTO_CREATE` (set to `false` for Supabase/hosted DB)

Flyway connection (optional override):
- `FLYWAY_URL`
- `FLYWAY_USER`
- `FLYWAY_PASSWORD`

If `FLYWAY_*` are not provided, Flyway falls back to `DATABASE_*`.

## Recommended roles

Use separate DB users when possible:
- App user: least privilege for runtime operations.
- Migration user: elevated privileges for DDL.

On Supabase free tier, you may start with the default `postgres` user, then split users later.

## First-time setup on a new (empty) Supabase DB

1. Set environment variables (`DATABASE_*` and optionally `FLYWAY_*`).
2. Ensure `DATABASE_AUTO_CREATE=false`.
3. Run migration:
   - Windows PowerShell:
     - `./gradlew.bat flywayMigrate`
   - macOS/Linux:
     - `./gradlew flywayMigrate`
4. Verify applied migrations:
   - `./gradlew.bat flywayInfo` (or `./gradlew flywayInfo`)
5. Start backend and run tests.

## Existing DB already created by old runtime auto-create

If your Supabase DB already has tables created outside Flyway:

1. Do NOT run `flywayMigrate` immediately (it will conflict with existing objects).
2. Baseline the existing schema state once:
   - `./gradlew.bat flywayBaseline -Dflyway.baselineVersion=1 -Dflyway.baselineDescription=legacy_schema`
3. After baseline, create new migrations only as `V2+` files.

## Adding a new schema change

1. Create a new SQL file in migration folder, e.g.:
   - `V2__add_tournament_status.sql`
2. Put forward-only SQL (no edits to old applied files).
3. Run:
   - `./gradlew.bat flywayMigrate`
   - `./gradlew.bat flywayValidate`
4. Commit migration with code changes.

## Safety rules

- Never edit an already-applied migration in shared environments.
- Never manually alter schema in Supabase dashboard without backfilling a migration.
- Keep `flyway.cleanDisabled=true` (already configured) to avoid accidental destructive clean.

## Rollback strategy

Flyway Community is forward-only by default. Rollback should be handled with:
- pre-deploy backups,
- compensating forward migrations,
- staged rollout (local -> staging-like clone -> production).
