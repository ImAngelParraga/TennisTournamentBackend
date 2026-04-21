# CONTINUITY

Last Updated: 2026-04-21
Repository: TennisTournamentBackend

## Update Rule
Update this file after each meaningful implementation/review/change in this repo.
Include: branch, uncommitted state, what changed, what remains.

## Current State
- Branch: `feat/auth-coverage-mutation-paths`
- Working tree status before this update: feature work in progress
- Main documentation entrypoint: `MODEL_CONTEXT.md`
- Prioritized backlog: `docs/ISSUES.md`
- Local implementation changes after this update:
  - modified: `CONTINUITY.md`
  - modified: `docs/ISSUES.md`
  - added: `docs/USER_RACKETS_STRINGINGS_PLAN.md`
  - modified: `docs/postman/TennisTournamentBackend.postman_collection.json`
  - modified: `src/main/kotlin/bros/parraga/db/DatabaseTables.kt`
  - modified: `src/main/kotlin/bros/parraga/modules/Koin.kt`
  - modified: `src/main/kotlin/bros/parraga/modules/Routing.kt`
  - added: `src/main/kotlin/bros/parraga/db/schema/RacketEntity.kt`
  - added: `src/main/kotlin/bros/parraga/domain/RacketDtos.kt`
  - added: `src/main/kotlin/bros/parraga/routes/RacketRoute.kt`
  - added: `src/main/kotlin/bros/parraga/services/repositories/racket/RacketRepository.kt`
  - added: `src/main/kotlin/bros/parraga/services/repositories/racket/RacketRepositoryImpl.kt`
  - added: `src/main/kotlin/bros/parraga/services/repositories/racket/dto/CreateRacketRequest.kt`
  - added: `src/main/kotlin/bros/parraga/services/repositories/racket/dto/CreateRacketStringingRequest.kt`
  - added: `src/main/kotlin/bros/parraga/services/repositories/racket/dto/UpdateRacketRequest.kt`
  - added: `src/main/kotlin/bros/parraga/services/repositories/racket/dto/UpdateRacketStringingRequest.kt`
  - added: `src/main/resources/db/migration/V8__user_rackets_and_stringings.sql`
  - added: `src/test/kotlin/bros/parraga/RacketRepositoryTest.kt`
  - added: `src/test/kotlin/bros/parraga/MutationAuthorizationCoverageTest.kt`

## Recent Completed Work
- (uncommitted in current session) Implemented the P1 authorization coverage expansion and closed the backlog item:
  - added `src/test/kotlin/bros/parraga/MutationAuthorizationCoverageTest.kt` with a seeded auth fixture covering the authenticated mutation surface end-to-end
  - added explicit `401` coverage for all authenticated mutation routes across clubs, club admins, players, tournaments, tournament flow, tournament players, matches, disabled `/users` writes, and owner-only racket routes
  - added explicit `403` deny coverage for outsider access across club/tournament/match manager routes and for player/racket ownership boundaries
  - added admin happy-path coverage for club, tournament, tournament-flow, tournament-player, and match-scoring mutations
  - covered auth-sensitive edge cases for owner-not-manageable admin operations and tournament moves into unmanaged clubs
  - moved the authorization coverage issue from open P1 into `Recently Completed (No Longer Missing)` in `docs/ISSUES.md`
  - validated with:
    - `./gradlew.bat test --no-daemon --tests "bros.parraga.MutationAuthorizationCoverageTest" --tests "bros.parraga.AuthorizationFlowTest" --tests "bros.parraga.PlayerTest" --tests "bros.parraga.UserTest" --tests "bros.parraga.RacketRepositoryTest"` (pass)
    - `./gradlew.bat test --no-daemon` (pass)
- (uncommitted in current session) Planned the remaining P1 authorization coverage work and cleaned up backlog placement:
  - moved already-completed P1 items for Group/Swiss support and validation tightening into `Recently Completed (No Longer Missing)` in `docs/ISSUES.md`
  - expanded the open authorization coverage item with an implementation-oriented plan covering the full authenticated mutation surface
  - recorded current observed auth test gaps: club/admin mutation coverage, explicit deny-path coverage across most tournament mutations, match score authorization denies, and broader owner-only deny coverage consistency
- (uncommitted in current session) Implemented user-owned rackets and stringing history MVP:
  - added planning doc `docs/USER_RACKETS_STRINGINGS_PLAN.md`
  - added DB schema + Flyway migration `V8__user_rackets_and_stringings.sql` for `rackets` and `racket_stringings`
  - rackets support `PUBLIC` / `PRIVATE` visibility, multiple rackets per user, and soft delete
  - stringing history stores `stringingDate`, separate mains/crosses tensions, free-text mains/crosses string types, and performance notes
  - added owner routes under `/users/me/rackets` for list/detail/create/update/delete and stringing create/update/delete
  - added public routes under `/users/{id}/rackets` for visible racket list/detail
  - owner reads include private rackets; public reads only expose `PUBLIC` rackets
  - deleting a racket soft-deletes the racket and all active stringings; deleting a stringing soft-deletes only that entry
  - updated Postman collection with the new racket endpoints and request payloads
  - validated with:
    - `./gradlew.bat --stop; $env:GRADLE_OPTS='-Dkotlin.compiler.execution.strategy=in-process'; ./gradlew.bat test --no-daemon --tests 'bros.parraga.RacketRepositoryTest'` (pass)
    - `./gradlew.bat --stop; $env:GRADLE_OPTS='-Dkotlin.compiler.execution.strategy=in-process'; ./gradlew.bat test --no-daemon` (pass)
- (planning only, no code changes yet) Clarified racket stringing feature scope with user:
  - users can manage multiple rackets
  - each stringing entry stores separate mains/crosses tensions
  - only the owning user can create/manage rackets and stringing history
  - racket visibility is user-controlled and can be changed later
  - visibility options are only `PUBLIC` and `PRIVATE`
  - string type will be stored as free text for mains and crosses separately
  - rackets and stringing history should use soft delete behavior
  - previous stringer-driven QR/public-token model is now considered over-scoped for the next implementation pass
- (local repo operation, not pushed) Fast-forward merged `feat/user-profile-achievements` into local `master`, then checked out `feat/user-profile-achievements` again to continue feature planning
- (planning only, no code changes yet) Investigated racket string tension history support:
  - current `/users` writes remain intentionally disabled, so authenticated stringing writes should use a dedicated subresource rather than generic user updates
  - current `GET /users/{id}` is the only expanded user detail response and can optionally expose racket history if the feature should be public
  - existing branch `feat/racket-stringing-qr-history` contains a broader prototype (`rackets`, `racket_stringings`, public token route, audit trail, tests) but it predates current context/docs consolidation and would need selective porting plus a new migration number
- (uncommitted in current session) Implemented user profile achievements MVP:
  - added tournament champion persistence and Flyway migration `V6__tournament_champion_player.sql`
  - persisted a single champion when tournaments complete; for Group/Swiss ties on top `points`, selected the lowest `player_id` deterministically
  - added DB-backed achievement definitions via `V7__achievement_definitions.sql` and `AchievementEntity.kt`
  - exposed DB-backed `achievements` only on `GET /users/{id}` directly on `User`
  - kept `GET /users` unchanged
  - added `.kotlin/` to `.gitignore`
  - updated Postman user request descriptions to reflect the detail response contract
  - validated with:
    - `./gradlew.bat --stop; $env:GRADLE_OPTS='-Dkotlin.compiler.execution.strategy=in-process'; ./gradlew.bat test --no-daemon --tests "bros.parraga.UserTest"` (pass)
    - `./gradlew.bat --stop; $env:GRADLE_OPTS='-Dkotlin.compiler.execution.strategy=in-process'; ./gradlew.bat test --no-daemon --tests "bros.parraga.TournamentRepositoryTest"` (pass)
    - `./gradlew.bat --stop; $env:GRADLE_OPTS='-Dkotlin.compiler.execution.strategy=in-process'; ./gradlew.bat test --no-daemon` (pass)
- (uncommitted in current session) Added planning docs for user profile achievement badges:
  - added backlog item in `docs/ISSUES.md` for tournament-win badges on user profiles
  - added `docs/USER_PROFILE_ACHIEVEMENTS_DRAFT.md` with MVP analysis, tie-aware winner persistence, single-user achievements response shape, future achievement ideas, the rule that Group/Swiss tied winners are determined by equal top `points` only, the decisions that only registered users get profile achievements and invalidated results do not auto-revoke them, and the policy that admins may manually recompute winner rows later
- (uncommitted in current session) Removed stale onboarding and handoff docs:
  - deleted `AI_CONTEXT.md` after replacing it with `MODEL_CONTEXT.md`
  - deleted `docs/SESSION_HANDOFF.md` and `docs/SESSION_HANDOFF_CONSOLIDATED.md`
  - updated `docs/START_HERE.md` to point future agents to `MODEL_CONTEXT.md` instead of older onboarding/handoff docs
- (uncommitted in current session) Consolidated model-facing project context:
  - added `MODEL_CONTEXT.md` as the canonical single-file context for future coding agents
  - updated `AGENTS.md` to require `MODEL_CONTEXT.md` and record local Qwen endpoint usage guidance
  - switched `CONTINUITY.md` main documentation entrypoint from `docs/START_HERE.md` to `MODEL_CONTEXT.md`
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
  - later superseded by `MODEL_CONTEXT.md` as the canonical single-file agent context
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
1. Add API contract documentation (OpenAPI/Swagger).
2. Add operational observability for progression/auth decisions.
3. Add cross-repo compatibility checks between backend and lib.
4. Design multi-context ranking sources for future seeding.

## Cross-Repo Dependency Notes
- Backend depends on `../TennisTournamentLib` via composite build substitution.
- Changes to lib behavior can affect backend progression and score semantics.

## Test Commands
- Backend tests:
  - `GRADLE_OPTS=-Dkotlin.compiler.execution.strategy=in-process ./gradlew test --no-daemon`
- Windows/Kotlin daemon cache note:
  - If lock/caching issues appear, run `./gradlew --stop` and retry with `--no-daemon`.

## Next Suggested Actions
1. Implement OpenAPI/Swagger docs and align examples with current auth/docs paths.
2. Add observability around progression and authorization decisions.
3. Add CI compatibility checks against `../TennisTournamentLib`.
