# Session Handoff (TennisTournamentBackend)

## 1) What This Project Is

`TennisTournamentBackend` is a Ktor + Exposed backend to manage tennis tournaments, players, clubs, phases, and matches.

Current product focus:
- Knockout tournaments first.
- Client sends results, backend auto-progresses bracket internals.
- Local dev should work without managing an external DB (H2 fallback).

It integrates with `../TennisTournamentLib` for bracket generation and score logic.

## 2) Tech Stack and Architecture

- Kotlin JVM 21
- Ktor 3 (`netty`, routing, content negotiation, CORS)
- Exposed DAO + JDBC (`postgresql` + `h2`)
- Koin DI
- Kotlinx serialization + datetime
- Composite build to local lib in `settings.gradle.kts`

Main app wiring:
- `src/main/kotlin/bros/parraga/Application.kt`
- Modules in `src/main/kotlin/bros/parraga/modules`

## 3) Current Runtime Behavior

### Database
- If `DATABASE_*` env vars are missing, app starts with in-memory H2:
  - `jdbc:h2:mem:dev;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;`
- Auto schema creation is enabled by default (`DATABASE_AUTO_CREATE=true`).
- File: `src/main/kotlin/bros/parraga/modules/DatabaseConfig.kt`

### Security and CORS
- Auth/security is not implemented yet (`configureSecurity()` is empty).
- CORS is already configured and currently permissive (`anyHost()`).
- Files:
  - `src/main/kotlin/bros/parraga/modules/Security.kt`
  - `src/main/kotlin/bros/parraga/modules/HTTP.kt`

### Error Handling
- Shared API wrapper: `ApiResponse(status, data, message)`.
- Central route helper maps common exceptions to HTTP status codes.
- File: `src/main/kotlin/bros/parraga/routes/RoutingUtils.kt`

## 4) Tournament Features Implemented

### Tournament data endpoints (split)
- `GET /tournaments` -> basic tournament list
- `GET /tournaments/{id}` -> basic tournament info
- `GET /tournaments/{id}/players`
- `GET /tournaments/{id}/phases`
- `GET /tournaments/{id}/matches`
- `GET /tournaments/{id}/bracket` (round-grouped, phase-grouped view)

### Phase creation
- `POST /tournaments/{id}/phases`
- Currently enforces `KNOCKOUT` only.
- Uses config payload with:
  - `thirdPlacePlayoff`
  - `qualifiers` (power of two, >= 1)

### Tournament start and progression
- `POST /tournaments/{id}/start`
- Computes rounds from `playerCount` and `qualifiers`.
- Generates matches using `TennisTournamentLib`.
- Persists dependencies for automatic progression.
- Applies walkovers and triggers progression callback.

### Match endpoints
- `GET /matches/{id}`
- `PUT /matches/{id}/score`
- Score update maps backend score -> lib score and uses lib `Match.applyScore()` for winner calculation.
- On completion, triggers `TournamentProgressionService.onMatchCompleted()`.

Key files:
- `src/main/kotlin/bros/parraga/services/repositories/tournament/TournamentRepositoryImpl.kt`
- `src/main/kotlin/bros/parraga/services/repositories/match/MatchRepositoryImpl.kt`
- `src/main/kotlin/bros/parraga/services/TournamentProgressionService.kt`
- `src/main/kotlin/bros/parraga/routes/TournamentRoute.kt`
- `src/main/kotlin/bros/parraga/routes/MatchRoute.kt`

## 5) Cross-Repo Dependency Notes (Important)

This backend depends on lib behavior for:
- knockout bracket generation,
- qualifiers round logic,
- third-place match generation,
- score winner calculation.

Local composite setup:
- `settings.gradle.kts` includes `../TennisTournamentLib` and substitutes `com.github.ImAngelParraga:TennisTournamentLib` with the local project.

Recent lib changes expected by backend:
- qualifiers in `KnockoutConfig`
- third-place support
- `Match.applyScore()` usage

## 6) Current Gaps / Missing Work

### High-impact missing
- Auth/authorization (currently absent).
- Domain-level match state hardening (scoring guards are still minimal for invalid state transitions).
- Deterministic seeding (lib currently shuffles players randomly).

### Reliability/operational gaps
- Lifecycle rules are permissive (intentional for now), so there are weak guards around post-start edits.
- `SchemaUtils.createMissingTablesAndColumns` is deprecated for robust production migrations; migration tooling is not in place.
- No explicit tournament status state machine (`DRAFT/STARTED/COMPLETED`).
- Concurrency/idempotency hardening should be improved for repeated progress/update calls.

### Scope gaps
- Group and Swiss are not fully supported as an active product path right now (phase creation currently restricts to knockout).

## 7) Tests and QA State

Backend tests run and pass recently after bracket work.
- Command used on Windows due Kotlin daemon cache/file lock issues:
  - `GRADLE_OPTS=-Dkotlin.compiler.execution.strategy=in-process ./gradlew test --no-daemon`

Current test strength:
- Basic repository integration tests.
- Bracket endpoint test (including third-place expectation in final round).

Missing test depth:
- Full knockout E2E flow with multiple rounds of scoring.
- Race/idempotency tests for repeated start and repeated score submits.
- Validation edge cases around invalid score transitions.

## 8) Postman and Manual Testing

- Collection: `postman/TennisTournamentBackend.postman_collection.json`
- Sequence guide: `postman/TEST_SEQUENCES.md`
- Includes bracket sequence and assertions.

## 9) Important Recent Commits (Backend)

- `b6debe9` Add bracket endpoint and third-place tests
- `d9add1a` Wire knockout qualifiers and lib scoring
- `3b82df0` Add knockout qualifiers flow and split tournament endpoints

## 10) Recommended Next Steps (Priority Order)

1. Implement authn/authz baseline.
- At minimum, protect mutation endpoints.
- Keep reads open if desired for early frontend iteration.

2. Add deterministic seeding support.
- Define API contract for seeding input.
- Wire through backend -> lib.

3. Add match state validation rules.
- Reject score updates when players are missing.
- Define whether re-scoring completed matches is allowed.

4. Add tournament status model.
- `DRAFT`, `STARTED`, `COMPLETED`.
- Use status checks to simplify endpoint rule decisions.

5. Improve migration strategy.
- Replace auto-mutate schema reliance in production with migration scripts/tooling.

6. Expand E2E integration tests.
- Build one realistic knockout flow with byes, qualifiers, third-place, and progression assertions.

## 11) Known Local Repo State

At this handoff moment:
- Backend repo is ahead of origin by 3 commits.
- `.aiignore` is staged (`A .aiignore`) per user choice to keep it.
