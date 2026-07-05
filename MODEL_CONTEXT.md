# Model Context: TennisTournamentBackend

## Purpose
This is the canonical condensed context file for coding agents working in `TennisTournamentBackend`.
Read this file first, then `CONTINUITY.md`.

Use this file for the current project baseline, workflow rules, architecture map, domain behavior, and active backlog.
Use the docs under `docs/` as supporting references when you need deeper detail.

## Repository Topology
- Backend repo: `C:\Users\ranki\IdeaProjects\TennisTournamentBackend`
- Library repo: `C:\Users\ranki\IdeaProjects\TennisTournamentLib`
- Backend consumes the lib through a composite build (`../TennisTournamentLib`).
- Changes to phase configuration, seeding, scoring, or progression semantics may require validation in both repos.

## Workflow Rules
- Do not commit or push unless the user explicitly asks.
- If a commit is requested, push immediately after each commit.
- Update `CONTINUITY.md` after each meaningful implementation or review change.
- Keep `docs/postman/TennisTournamentBackend.postman_collection.json` in sync with API changes.
- Any DB schema change must be a new Flyway migration under `src/main/resources/db/migration/`.
- Never rewrite old migrations already applied in shared environments.
- Keep secrets in `local.properties` or other gitignored configuration, not in tracked files.

## Tech Stack
- Kotlin JVM 21
- Ktor 3
- Exposed DAO + JDBC
- Koin DI
- H2 for local/test fallback
- PostgreSQL for hosted environments
- Clerk-compatible JWT verification (`clerk-jwt`)

## Runtime and Configuration
- Main build files: `build.gradle.kts`, `settings.gradle.kts`
- App entrypoint: `src/main/kotlin/bros/parraga/Application.kt`
- Core modules live under `src/main/kotlin/bros/parraga/modules/`
- If `DATABASE_*` vars are missing, the app falls back to in-memory H2.
- Hosted environments should set `DATABASE_AUTO_CREATE=false` and rely on Flyway.
- Dev seed data: `configureSeeding()` (`modules/SeedConfig.kt` + `db/seed/SeedData.kt`) runs after
  `configureDatabase()` when `SEED_DATA=true`, refuses non-H2 DBs unless `SEED_FORCE=true`, is
  idempotent (sentinel user `seed-owner`), and drives the real repository layer to build DRAFT /
  STARTED / COMPLETED tournaments plus GROUP/SWISS samples. `run-local.ps1` sets `SEED_DATA=true`.

Important environment variables:
- `DATABASE_URL`
- `DATABASE_DRIVER`
- `DATABASE_USER`
- `DATABASE_PASSWORD`
- `DATABASE_AUTO_CREATE`
- `CLERK_ISSUER`
- `CLERK_AUDIENCE`
- `ALLOWED_ORIGINS`
- `AUTH_TEST_JWT_SECRET` (tests/local auth flows)
- `PORT` (HTTP listen port; defaults to `8080`; Cloud Run injects it)
- `SEED_DATA` (`true` loads dev seed data on startup; H2 fallback only)
- `SEED_FORCE` (`true` allows seeding a non-H2 DB; leave unset in shared envs)

## Auth and Authorization
- Public reads are open.
- Writes require JWT auth via `clerk-jwt`.
- Write authorization uses owner/admin checks.
- Platform role: `users.role` (`USER` | `PLATFORM_ADMIN`, migration `V15`). Clubs are provisioned
  manually by the platform operator: `POST /clubs` (with required `ownerUserId`) and
  `DELETE /clubs/{id}` require `PLATFORM_ADMIN`. The role is assigned via manual SQL only
  (`UPDATE users SET role = 'PLATFORM_ADMIN' WHERE auth_subject = '<sub>';`), never via API.
- Club owners can manage their clubs and tournaments under those clubs (edit + admins; not create/delete club).
- Club admins can also perform club/tournament/match write operations.
- `GET /users/me` returns `role` and `managedClubIds` (owned + administered clubs) for frontend gating.
- `/users` write endpoints are intentionally disabled and return `403`.
- On first authenticated write, a local `users` row is auto-created for the JWT subject — unless an
  unclaimed row (`auth_subject IS NULL`) exists with the token's verified email, in which case that
  row is claimed (subject bound). Used by the seeded test personas; see `docs/MANUAL_TESTING.md`.

Important auth files:
- `src/main/kotlin/bros/parraga/modules/AuthConfig.kt`
- `src/main/kotlin/bros/parraga/routes/AuthUtils.kt`
- `src/main/kotlin/bros/parraga/services/auth/AuthorizationService.kt`

## Domain Baseline
- The backend manages clubs, players, tournaments, phases, and matches for tennis competitions.
- Supported phase formats: `KNOCKOUT`, `GROUP`, `SWISS`.
- Knockout is still the most battle-tested path.
- Tournament lifecycle statuses: `DRAFT`, `STARTED`, `COMPLETED`, `CANCELLED`, `ABANDONED`.
- Reset endpoint: `POST /tournaments/{id}/reset`.
- Bracket/progression logic is delegated to `TennisTournamentLib` plus backend orchestration.
- Players can request to join DRAFT tournaments through join requests; club owners/admins accept/reject requests.
- Accepted join requests create rows in `tournament_players`; pending join requests expire when the tournament starts.

Current behavior notes:
- Group uses single round-robin generation.
- Swiss uses standings-based pairing and currently has no rematch-avoidance rule.
- Swiss cross-phase advancement is controlled by `advancingCount`.
- If `advancingCount` is omitted, Swiss advances all players to the next phase.
- Match score submission is hardened around match state and payload validity.
- Start/progression paths include concurrency and idempotency guardrails.

## Seeding Status
- Backend persists optional tournament seed in `tournament_players.seed` via Flyway `V4__tournament_player_seeding.sql`.
- Tournament add-players payload supports optional `seed`.
- Knockout phase configuration supports `seedingStrategy`.
- Tournament start builds seeded participants and calls the lib participant-based API.
- Group/Swiss seeded behavior is still future work.
- Future ranking-source work is tracked separately in `docs/ISSUES.md`.

## Data and Migration Rules
- Flyway migration folder: `src/main/resources/db/migration/`
- Existing migrations include baseline/lifecycle/seeding/progression guardrails work.
- Do not edit previously applied migrations.
- For DB changes, when credentials are available, run:
  - `./gradlew.bat flywayInfo --no-daemon`
  - `./gradlew.bat flywayMigrate --no-daemon`
  - `./gradlew.bat flywayValidate --no-daemon`

Detailed references:
- `docs/DB_MIGRATIONS.md`
- `docs/DB_BASELINE_STATUS.md`

## Testing Rules
- Run targeted tests for the changed area before finishing.
- Run the full backend test suite when changes are broad.
- If backend-lib contracts change, validate both repos.

Useful commands:
- Backend tests: `./gradlew.bat test --no-daemon`
- Lib tests: `../TennisTournamentLib/gradlew.bat test --no-daemon`
- On Windows, if Kotlin daemon/caching issues appear, stop Gradle and retry with `--no-daemon`.

## API Surface Summary
Public reads:
- `GET /clubs`
- `GET /clubs/{id}`
- `GET /clubs/{id}/admins`
- `GET /players`
- `GET /players/{id}`
- `GET /users`
- `GET /users/{id}`
- `GET /tournaments`
- `GET /tournaments/{id}`
- `GET /tournaments/{id}/players`
- `GET /tournaments/{id}/phases`
- `GET /tournaments/{id}/matches`
- `GET /tournaments/{id}/bracket`
- `GET /matches/{id}`

Public writes:
- `POST /club-contact-requests` — club onboarding contact form (anonymous; operator reviews via admin GET)

Authenticated writes:
- Clubs: `POST /clubs` (platform admin), `PUT /clubs`, `DELETE /clubs/{id}` (platform admin)
- Club contact requests: `GET /club-contact-requests`, `DELETE /club-contact-requests/{id}` (platform admin)
- Club admins: `POST /clubs/{id}/admins/{userId}`, `DELETE /clubs/{id}/admins/{userId}`
- Players: `POST /players`, `PUT /players`, `DELETE /players/{id}`
- Tournaments: `POST /tournaments`, `PUT /tournaments`, `DELETE /tournaments/{id}`
- Tournament flow: `POST /tournaments/{id}/start`, `POST /tournaments/{id}/reset`
- Tournament phases: `POST /tournaments/{id}/phases`
- Tournament players: `POST /tournaments/{id}/players`, `DELETE /tournaments/{id}/players/{playerId}`
- Tournament join requests:
  - player: `POST /tournaments/{id}/join-requests`, `POST /tournaments/{id}/join-requests/{requestId}/withdraw`, `GET /users/me/tournament-join-requests`
  - manager: `GET /tournaments/{id}/join-requests`, `POST /tournaments/{id}/join-requests/{requestId}/accept`, `POST /tournaments/{id}/join-requests/{requestId}/reject`, `POST /tournaments/{id}/join-requests/{requestId}/allow-resubmit`
- Match scoring: `PUT /matches/{id}/score`

Response/error contract:
- Responses use `ApiResponse(status, data, message)`.
- Common error mapping: `400`, `401`, `403`, `404`, `409`, `500` depending on validation/auth/domain failures.

## Important Code Areas
- Routing: `src/main/kotlin/bros/parraga/routes/`
- Runtime modules: `src/main/kotlin/bros/parraga/modules/`
- Repository/services: `src/main/kotlin/bros/parraga/services/`
- Tournament repository: `src/main/kotlin/bros/parraga/services/repositories/tournament/TournamentRepositoryImpl.kt`
- Match repository: `src/main/kotlin/bros/parraga/services/repositories/match/MatchRepositoryImpl.kt`
- Phase execution: `src/main/kotlin/bros/parraga/services/PhaseExecutionService.kt`
- Progression orchestration: `src/main/kotlin/bros/parraga/services/TournamentProgressionService.kt`
- Schema: `src/main/kotlin/bros/parraga/db/schema/`

## Documentation Map
Use these when you need more depth than this file provides:
- `docs/START_HERE.md`: broader onboarding and architecture map
- `docs/ISSUES.md`: prioritized backlog
- `docs/AUTH_SETUP.md`: auth env/setup details
- `docs/DB_MIGRATIONS.md`: Flyway workflow
- `docs/DB_BASELINE_STATUS.md`: recorded migration snapshot
- `docs/postman/TennisTournamentBackend.postman_collection.json`: API collection
- `docs/postman/TEST_SEQUENCES.md`: manual validation flows

## Manual Validation Notes
- Register players before creating entrant-count-sensitive phases.
- Create at least one valid phase before starting a tournament.
- Use `GET /tournaments/{id}/matches` to inspect generated matches and advancement.
- Helpful Postman scenarios include:
  - tournament start idempotency
  - knockout auto-progress
  - Swiss round creation
  - bracket view and third-place verification

## Current Priorities
From `docs/ISSUES.md`:
1. Expand authorization test coverage for mutation paths and edge cases.
2. Add API contract documentation (OpenAPI/Swagger).
3. Add operational observability for progression/auth decisions.
4. Add cross-repo compatibility checks between backend and lib.
5. Design multi-context ranking sources for future seeding.
6. Add deployment-time `flywayMigrate` before app startup.
7. Allow users to create tournaments/leagues without a club.

## Known Risks and Constraints
- Knockout is the most mature format; Group/Swiss should be validated carefully in end-to-end flows.
- Backend behavior depends heavily on lib contracts for progression, seeding, scoring, and phase semantics.
- Hosted environments must not rely on runtime schema auto-mutation.
- API docs are still incomplete until OpenAPI/Swagger is added.

## Read Order for Future Agents
1. `AGENTS.md`
2. `MODEL_CONTEXT.md`
3. `CONTINUITY.md`
4. Additional source/docs only as needed for the task at hand
