# CONTINUITY

Last Updated: 2026-03-15
Repository: TennisTournamentBackend

## Update Rule
Update this file after each meaningful implementation/review/change in this repo.
Include: branch, uncommitted state, what changed, what remains.

## Current State
- Branch: `master`
- Local note: keep working tree clean between tasks; this file may appear as a local change until committed.
- Main documentation entrypoint: `docs/START_HERE.md`
- Prioritized backlog: `docs/ISSUES.md`
- Local implementation changes (not committed yet):
  - modified: `CONTINUITY.md`
  - modified: `docs/ISSUES.md`
  - modified: `docs/postman/TEST_SEQUENCES.md`
  - modified: `src/main/kotlin/bros/parraga/services/PhaseExecutionService.kt`
  - modified: `src/main/kotlin/bros/parraga/services/repositories/tournament/TournamentRepositoryImpl.kt`
  - modified: `src/test/kotlin/bros/parraga/TournamentRepositoryTest.kt`

## Recent Completed Work
- (uncommitted in current session) Tightened tournament and phase validation inputs:
  - enforce `startDate <= endDate` on tournament create/update
  - validate draft phase definitions against projected entrant counts derived from current players and earlier phase configs
  - reject draft player add/remove operations when they would invalidate existing phase setup
  - improve knockout qualifier error messaging to show projected/player counts and allowed values
  - documented that entrant-count-sensitive phases should be created after players are registered
  - added backend integration coverage for invalid dates, invalid phase entrant counts, projected knockout qualifier failures, and draft player changes that break phase config
  - validated with:
    - `./gradlew.bat test --no-daemon --tests "bros.parraga.TournamentRepositoryTest"` (pass)
    - `./gradlew.bat test --no-daemon` (pass)
    - `../TennisTournamentLib/gradlew.bat test --no-daemon` (pass; Kotlin daemon fell back but build succeeded)
- (uncommitted in current session) Implemented Group and Swiss end-to-end support across lib-backed backend flow:
  - phase creation now accepts `GROUP` and `SWISS` configs
  - added shared phase execution service for phase start and next-phase auto-start
  - added group persistence (`groups`, `group_standings`) and Swiss ranking snapshots
  - group completion can advance qualified players into later phases
  - Swiss completion creates later rounds automatically
  - Swiss cross-phase advancement is controlled by `advancingCount`; omitted means all players advance
  - reset now clears generated groups and Swiss rankings
  - added backend integration tests for group phase creation, Swiss round progression, and group-to-knockout advancement
  - validated with:
    - `./gradlew.bat test --no-daemon --tests "bros.parraga.TournamentRepositoryTest"` (pass)
    - `./gradlew.bat test --no-daemon` (pass)
- (uncommitted in current session) Implemented P0 concurrency/idempotency guardrails for start/progression:
  - added row-level locking helpers (`RowLocking.kt`) and applied locks in tournament start, match scoring, and progression paths
  - added idempotent replay behavior for completed match scoring when payload matches existing score
  - added deterministic `round_slot` assignment and unique round-slot DB integrity guardrail
  - added migration `V5__match_progression_guardrails.sql` with round-slot backfill/constraints and dependency self-reference check
  - added integration tests for repeated/concurrent start and same-payload score replay
  - validated with:
    - `./gradlew.bat test --no-daemon --tests "bros.parraga.TournamentRepositoryTest"` (pass)
    - `./gradlew.bat test --no-daemon` (pass)
- (uncommitted in current session) Marked start/progression concurrency-idempotency issue as completed in `docs/ISSUES.md`
- (uncommitted in current session) Hardened match score submission rules:
  - reject score updates unless match status is `SCHEDULED` or `LIVE`
  - reject scoring until both players are assigned
  - validate structural score payload integrity (non-empty sets, non-negative values, no tied unresolved sets/tiebreaks)
  - added integration tests for completed/non-ready/walkover/invalid-payload scoring attempts
  - validated with `./gradlew.bat test --no-daemon` (pass)
- (uncommitted in current session) Marked score hardening issue as completed in `docs/ISSUES.md`
- (uncommitted in current session) Marked deterministic seeding issue as completed in `docs/ISSUES.md`
- (uncommitted in current session) Added AI operating docs:
  - `AGENTS.md` with workflow rules (commit/push policy, flyway/postman/update discipline)
  - `AI_CONTEXT.md` as backend+lib quick-start context for future models
- (uncommitted in current session) Refreshed Postman collection to match all current routes:
  - `docs/postman/TennisTournamentBackend.postman_collection.json`
  - added missing endpoints (including tournament reset) and aligned payloads with current DTOs
- (uncommitted in current session) Extended Postman phase examples for the new tournament formats:
  - documented `GROUP` phase creation as single round-robin
  - documented `SWISS` phase creation with both default "advance everyone" behavior and explicit `advancingCount`
- (uncommitted in current session) Added a runnable Postman multi-phase tournament scenario:
  - new `Scenarios` folder in `docs/postman/TennisTournamentBackend.postman_collection.json`
  - automates a zero-state `GROUP -> SWISS -> KNOCKOUT` tournament flow
  - generates a test JWT, creates the club, creates the tournament, registers players, runs the scoring loop, and verifies completion
- (uncommitted in current session) Added a second zero-state Postman scenario for odd-player Swiss:
  - `SWISS -> KNOCKOUT` with 5 players, round-1 bye coverage, and explicit `advancingCount = 4`
  - verifies Swiss walkover generation and tournament completion end-to-end
- (uncommitted in current session) Kept Postman end-to-end scenarios manual-only:
  - application runtime supports `AUTH_TEST_MODE=true` so local Postman scenario tokens can work outside `testModule()`
  - removed automation around those scenarios and left the Postman scenario folders as manual local tooling
- (uncommitted in current session) Started explicit seeding contract refactor:
  - added future issue for multi-context ranking sources in `docs/ISSUES.md`
  - added tournament-player seed persistence (`TournamentPlayersTable.seed`) + migration `V4__tournament_player_seeding.sql`
  - added `seed` to add-players request contract and seed conflict validation
  - wired tournament start to build `SeededParticipant` entries and call lib `startPhaseWithParticipants`
  - added tests for duplicate seed conflicts and partial-seeded round-1 behavior
- (uncommitted in current session) Drafted explicit seeding contract refactor plan:
  - `docs/SEEDING_CONTRACT_REFACTOR_DRAFT.md`
  - covers backend DB/API model with explicit seeds + lib participant contract + phased rollout
- (uncommitted in current session) Tournament lifecycle policy implementation:
  - added `CANCELLED`/`ABANDONED` tournament statuses (domain + migration `V3__tournament_extended_statuses.sql`)
  - post-start updates are metadata-only (`name`, `description`, `surface`)
  - controlled reset endpoint `POST /tournaments/{id}/reset` with guardrails (only `STARTED`, no completed matches)
  - progression now transitions tournament status to `COMPLETED` when terminal phase finishes
  - integration tests added for metadata updates, reset rules, and completed transition
- (uncommitted in current session) Tournament lifecycle enforcement:
  - draft-only tournament mutations (update/delete/add-remove players/create phase)
  - phase-order guards (`phaseOrder=1` required to start, unique/order checks)
  - tournament status persisted (`DRAFT` -> `STARTED`) with DB migration `V2__tournament_lifecycle_constraints.sql`
- (uncommitted in current session) Applied Flyway migration `V2__tournament_lifecycle_constraints.sql` on Supabase
- (uncommitted in current session) Recorded initial Flyway baseline snapshot in `docs/DB_BASELINE_STATUS.md`
- (uncommitted in current session) Flyway Gradle task robustness fixes:
  - added PostgreSQL Flyway plugin classpath for Flyway v12
  - simplified Flyway configuration to use only `DATABASE_*` or Gradle properties
- (uncommitted in current session) Flyway integration in Gradle + baseline migration `V1__baseline.sql`
- (uncommitted in current session) Runtime DB auto-create defaults to local fallback only
- (uncommitted in current session) New migration operations doc: `docs/DB_MIGRATIONS.md`
- `c402534` docs(issues): reprioritize missing work across backend and tournament lib
- `c06a34e` docs: move postman collection under docs/postman
- `3448107` docs: move markdown docs under docs and add onboarding guide
- `9abd641` docs(auth): add setup and session handoff notes
- `2fe817b` test(auth): cover authenticated write flows and authorization checks
- `707509d` feat(security-runtime): configure JWT auth and origin-based CORS
- `463c8f8` feat(authz-model): add local identity and club/player authorization primitives

## Current Functional Baseline
- JWT authentication is wired (`clerk-jwt`) with test-mode verifier support.
- Write endpoints are authorization-gated (club owner/admin model).
- Knockout flow supports qualifiers, byes, bracket endpoint, progression, and third-place (with constraints).
- Markdown docs were consolidated under `docs/` and `docs/postman/`.
- Flyway migration scaffolding is present for Postgres/Supabase.

## Highest Priority Remaining Work
(See `docs/ISSUES.md` for ordered list.)
1. Migration rollout in deployment flow (`flywayMigrate` gate + hosted env hardening).
2. Expand authorization test coverage for all mutation paths and edge cases.
3. Add API contract documentation (OpenAPI/Swagger).
4. Add operational observability for progression/auth decisions.

## Cross-Repo Dependency Notes
- Backend depends on `../TennisTournamentLib` via composite build substitution.
- Changes to lib behavior can affect backend progression and score semantics.

## Test Commands
- Backend tests:
  - `GRADLE_OPTS=-Dkotlin.compiler.execution.strategy=in-process ./gradlew test --no-daemon`
- Windows/Kotlin daemon cache note:
  - If lock/caching issues appear, run `./gradlew --stop` and retry with `--no-daemon`.

## Next Suggested Actions
1. Pick first P0 from `docs/ISSUES.md` and implement with tests.
2. Add API contract docs (OpenAPI) after lifecycle rules stabilize.
3. Add CI cross-repo compatibility checks with TennisTournamentLib.
