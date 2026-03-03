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

## Highest Priority Remaining Work
(See `docs/ISSUES.md` for ordered list.)
1. Production migration strategy (replace runtime schema mutation for production).
2. Lifecycle/state enforcement for tournaments and matches.
3. Stronger match scoring validation.
4. Concurrency/idempotency guardrails for start/progression.
5. Deterministic seeding end-to-end with lib.

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
