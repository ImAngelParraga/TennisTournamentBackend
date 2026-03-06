# AI Context: TennisTournamentBackend

## Read First
1. `AGENTS.md`
2. `CONTINUITY.md`
3. `docs/START_HERE.md`
4. `docs/ISSUES.md`

## Project Topology
- Backend repo: `C:\Users\ranki\IdeaProjects\TennisTournamentBackend`
- Lib repo: `C:\Users\ranki\IdeaProjects\TennisTournamentLib`
- Backend consumes lib through composite build (`../TennisTournamentLib`).

## Current API/Domain Highlights
- Public reads are open.
- Writes are auth-gated (`clerk-jwt`) with owner/admin authorization checks.
- Tournament lifecycle currently includes:
  - `DRAFT`, `STARTED`, `COMPLETED`, `CANCELLED`, `ABANDONED`
  - reset endpoint: `POST /tournaments/{id}/reset`
- Bracket/progression logic is knockout-first and delegated to lib generation + backend orchestration.

## Seeding Status
- Backend supports tournament-player seed persistence (`tournament_players.seed`) via Flyway `V4`.
- Add-players payload supports optional `seed`.
- Knockout phase config supports `seedingStrategy`.
- Start flow builds seeded participants and calls lib participant-based API.
- Group/Swiss seeded behavior is still future work.

## DB Migration Rules
- Use forward-only Flyway migrations under `src/main/resources/db/migration/`.
- Do not edit already-applied migrations.
- Typical commands:
  - `./gradlew.bat flywayInfo --no-daemon`
  - `./gradlew.bat flywayMigrate --no-daemon`
  - `./gradlew.bat flywayValidate --no-daemon`

## Testing Commands
- Backend tests: `./gradlew.bat test --no-daemon`
- If relevant, validate lib too: `../TennisTournamentLib/gradlew.bat test --no-daemon`

## API Documentation Discipline
- Postman collection must match current routes/DTOs:
  - `docs/postman/TennisTournamentBackend.postman_collection.json`
- Update this file whenever routes, auth requirements, params, or payload contracts change.

## User Workflow Preferences
- Do not commit/push unless user explicitly asks.
- When user asks to commit, push immediately after each commit.
- Keep `CONTINUITY.md` updated.
