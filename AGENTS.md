# AGENTS

## Scope
This file applies to `TennisTournamentBackend`.

## Required Workflow
1. Read `AI_CONTEXT.md` and `CONTINUITY.md` before making changes.
2. Do not commit or push unless the user explicitly asks.
3. If a commit is requested, push immediately after each commit.
4. Update `CONTINUITY.md` after each meaningful implementation/review change.
5. Keep `docs/postman/TennisTournamentBackend.postman_collection.json` in sync with API changes.

## Backend-Specific Rules
- Any DB schema change must be a new Flyway migration (`src/main/resources/db/migration/V*_*.sql`).
- Never rewrite old migrations already applied in shared environments.
- Keep secrets in `local.properties` (gitignored), not in tracked files.
- When DB changes are made and credentials are available, run:
  - `./gradlew.bat flywayInfo --no-daemon`
  - `./gradlew.bat flywayMigrate --no-daemon`
  - `./gradlew.bat flywayValidate --no-daemon`
- Run tests before finishing:
  - targeted tests for changed areas
  - full backend tests when changes are broad

## Cross-Repo Rules
- Backend uses `../TennisTournamentLib` via composite build.
- If backend-lib contracts change (phase config, seeding, scoring/progression semantics), validate both repos.
