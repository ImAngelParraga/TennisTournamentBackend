# Issues (Priority Ordered)

This list reflects the current state of both repos:
- Backend: `TennisTournamentBackend`
- Library: `TennisTournamentLib`

## P0 (Highest Priority)

- [ ] Replace runtime schema mutation with real DB migrations (Flyway/Liquibase) and disable `createMissingTablesAndColumns` in production.
  Why: current startup migration strategy is not safe for production rollback/recovery.

- [ ] Introduce explicit tournament/match lifecycle rules and enforce them in write endpoints.
  Why: current flow is too permissive after start.
  Missing rules:
  - lock player/phase mutations after tournament start
  - define whether tournament metadata is mutable post-start
  - ensure phase 1 exists and only one phase per `phaseOrder` within a tournament

- [ ] Harden match score submission rules.
  Why: scoring is currently permissive and relies on minimal checks.
  Missing rules:
  - reject scoring matches not in a scoreable state (for example already completed unless overwrite is explicitly allowed)
  - reject scoring when players are not both present
  - enforce legal tennis score validation (set/tiebreak consistency), not just greater-than comparisons

- [ ] Add concurrency/idempotency guardrails for start/progression.
  Why: duplicate writes are possible under concurrent requests.
  Missing protections:
  - transactional locking around `startTournament` and progression updates
  - DB-level uniqueness/integrity constraints to prevent duplicate match/dependency creation
  - clearly idempotent behavior for repeated `start` and repeated score updates

- [ ] Implement deterministic seeding end-to-end (backend contract + lib behavior).
  Why: `KnockoutService` currently shuffles players randomly, so brackets are non-reproducible.

## P1 (High Priority)

- [ ] Implement Group and Swiss in `TennisTournamentLib` (or remove/defer these formats from contracts).
  Why: `GroupService` and `SwissService` are still TODO in the lib while format types exist in shared models.

- [ ] Tighten tournament/phase validation inputs.
  Missing validations:
  - `startDate <= endDate` for tournament create/update
  - stronger qualifiers validation messaging against actual player counts and intended product rules

- [ ] Expand authorization test coverage for all mutation paths and edge cases.
  Why: auth baseline exists, but coverage is still skewed toward happy paths and a few deny cases.

## P2 (Medium Priority)

- [ ] Add API contract documentation (OpenAPI/Swagger) and keep examples aligned with current auth and docs paths.

- [ ] Add operational observability for progression/auth decisions.
  Missing pieces:
  - structured logs around `startTournament`, progression, and score updates
  - audit trail for privileged write operations

- [ ] Add cross-repo compatibility checks between backend and lib.
  Why: backend behavior depends heavily on lib semantics; compatibility should be validated in CI.

## Recently Completed (No Longer Missing)

- [x] JWT authentication baseline (Clerk-compatible verifier + test verifier)
- [x] CORS origin allow-list configuration
- [x] Role-based authorization for write endpoints (owner/admin model)
- [x] Auth setup and onboarding docs under `docs/`
