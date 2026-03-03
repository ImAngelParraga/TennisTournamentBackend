# Consolidated Session Handoff (TennisTournamentBackend + TennisTournamentLib)

## 1) Product Goal

Build a backend that lets clubs create/manage tournaments, register players, submit match results, and automatically progress tournaments.

Current intentional scope:
- Knockout first.
- No auth focus yet.
- Frontend can start against current APIs.

## 2) Repos and Relationship

### Main project (backend)
- Path: `C:\Users\ranki\IdeaProjects\TennisTournamentBackend`
- Ktor + Exposed + Koin app with REST endpoints and DB persistence.
- Owns API, persistence, progression orchestration, and integration behavior.

### Tournament engine library
- Path: `C:\Users\ranki\IdeaProjects\TennisTournamentLib`
- Owns tournament generation/progression logic and score-to-winner logic.
- Backend depends on this logic.

### Integration model
- `settings.gradle.kts` in backend includes `../TennisTournamentLib` as a composite build and substitutes `com.github.ImAngelParraga:TennisTournamentLib` with local source when available.

## 3) Current Backend State

### Stack
- Kotlin/JVM 21, Ktor 3, Exposed DAO, Koin, Kotlin serialization.
- H2 + PostgreSQL drivers.

### Runtime config
- If `DATABASE_*` vars are missing, backend starts with in-memory H2.
- Auto schema creation enabled by default.
- CORS is configured and permissive (`anyHost()`).
- `configureSecurity()` is currently empty (no authn/authz).

### Implemented endpoints (relevant)
- Tournament basics:
  - `GET /tournaments`
  - `GET /tournaments/{id}`
  - `POST /tournaments`
  - `PUT /tournaments`
  - `DELETE /tournaments/{id}`
- Tournament scoped data:
  - `GET /tournaments/{id}/players`
  - `GET /tournaments/{id}/phases`
  - `GET /tournaments/{id}/matches`
  - `GET /tournaments/{id}/bracket`
- Phase and lifecycle:
  - `POST /tournaments/{id}/phases`
  - `POST /tournaments/{id}/start`
- Match:
  - `GET /matches/{id}`
  - `PUT /matches/{id}/score`

### Knockout behavior implemented
- Phase creation currently enforces knockout format.
- Knockout config supports:
  - `thirdPlacePlayoff`
  - `qualifiers` (power-of-two required)
- `startTournament` computes rounds from qualifiers and player count.
- Match dependencies are persisted and used for progression.
- Walkovers are applied and progression is triggered.
- Score updates use lib `Match.applyScore()` to compute winner and mark completion, then trigger progression.

### API ergonomics recently added
- DTO split for tournament-related payloads.
- Dedicated `/bracket` endpoint returns bracket grouped by phase and round.

## 4) Current Library State (TennisTournamentLib)

### Implemented
- Knockout bracket generation.
- Bye handling (null-based bye representation).
- Dependency-based progression with winner/loser outcomes.
- Qualifiers + round calculation in lib (`computeRounds`).
- Third-place match generation from semifinal losers.
- Domain scoring function: `Match.applyScore(score)`.

### Not implemented
- `GroupService` and `SwissService` are TODO.
- Deterministic seeding is not implemented (currently shuffled/randomized).

### Important constraint
- Third-place currently requires `qualifiers == 1` and at least 4 players.

## 5) What Is Missing (Functional Knockout MVP Hardening)

High-value missing items:
- Auth/authz for mutation endpoints.
- Deterministic seeding strategy (input order or explicit seeds).
- Match state validation hardening (when scoring is accepted/rejected).
- Tournament status state machine (`DRAFT/STARTED/COMPLETED`) to simplify rule checks.
- Migration strategy for production (replace long-term reliance on auto schema mutation).
- Concurrency/idempotency guardrails for repeated start/progression updates.

Tracked checklist in backend:
- `docs/ISSUES.md`

## 6) Tests and Manual Validation State

### Automated
- Backend tests recently passing after bracket/third-place work.
- Lib tests passing.
- Windows note: if Kotlin daemon cache/file lock issues appear, run with:
  - `GRADLE_OPTS=-Dkotlin.compiler.execution.strategy=in-process`
  - and `--no-daemon`

### Manual
- Postman collection and sequences exist in backend:
  - `postman/TennisTournamentBackend.postman_collection.json`
  - `docs/postman/TEST_SEQUENCES.md`
- Includes bracket sequence and assertions.

## 7) Recent Commits (Context Snapshot)

### Backend
- `b6debe9` Add bracket endpoint and third-place tests
- `d9add1a` Wire knockout qualifiers and lib scoring
- `3b82df0` Add knockout qualifiers flow and split tournament endpoints

### Lib
- `dd3a556` Add third-place playoff bracket support
- `ef95218` Add qualifiers and scoring helpers
- `a62eccf` Fix knockout loser selection and bye winners

## 8) Recommended Next Steps (Prioritized)

1. Implement authn/authz baseline for write endpoints.
2. Implement deterministic seeding in lib and expose corresponding backend contract.
3. Add explicit match-state validation rules for score updates.
4. Add tournament status field + transition rules.
5. Expand end-to-end integration tests for knockout flow (including qualifiers, byes, third-place, repeated calls).
6. Introduce production migration tooling and reduce reliance on runtime schema mutation.

## 9) Current Local Working State (at handoff)

### Backend repo
- Ahead of origin by 3 commits.
- `.aiignore` staged (`A .aiignore`) by user preference.
- Existing handoff files:
  - `docs/SESSION_HANDOFF.md` (backend focused)

### Lib repo
- Ahead of origin by 4 commits.
- `.aiignore` staged (`A .aiignore`) by user preference.
- Existing handoff files:
  - `docs/SESSION_HANDOFF.md` (lib focused)

This file is the single consolidated reference for the next model.

