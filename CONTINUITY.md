# CONTINUITY

Last Updated: 2026-03-03
Repository: TennisTournamentBackend

## Update Rule
Update this file after each meaningful implementation/review/change in this repo.
Include: branch, uncommitted state, what changed, what remains.

## Current State
- Branch: `master`
- Local note: keep working tree clean between tasks; this file may appear as a local change until committed.
- Main documentation entrypoint: `docs/START_HERE.md`
- Prioritized backlog: `docs/ISSUES.md`

## Recent Completed Work
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
2. Stronger match scoring validation.
3. Concurrency/idempotency guardrails for start/progression.
4. Deterministic seeding end-to-end with lib.

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
