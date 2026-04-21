# Issues (Priority Ordered)

This list reflects the current state of both repos:

- Backend: `TennisTournamentBackend`
- Library: `TennisTournamentLib`

## P0 (Highest Priority)

- No open P0 items.

## P1 (High Priority)

- No open P1 items.

## P2 (Medium Priority)

- [ ] Add API contract documentation (OpenAPI/Swagger) and keep examples aligned with current auth and docs paths.

- [ ] Add operational observability for progression/auth decisions.
  Missing pieces:
    - structured logs around `startTournament`, progression, and score updates
    - audit trail for privileged write operations

- [ ] Add cross-repo compatibility checks between backend and lib.
  Why: backend behavior depends heavily on lib semantics; compatibility should be validated in CI.

- [ ] Design and implement multi-context ranking sources for future seeding.
  Why: future seeding should support rankings beyond a single global list.
  Missing pieces:
    - league-scoped ranking points from match/tournament results
    - users participating in multiple leagues with independent rankings
    - organizer/club-scoped ranking based only on tournaments they host
    - seeding policy selection (which ranking source applies per tournament/phase)

- [ ] Deployment pipeline (future): run `flywayMigrate` before app startup.
  Why: this is required once a hosting/deployment target is chosen.
  Scope:
    - add a pre-start migration step in deployment/release workflow
    - ensure hosted env keeps `DATABASE_AUTO_CREATE=false`
    - fail deployment if migrations fail

- [ ] Add feature to allow users to create tournaments/leagues without a club.
  Why: clubs usually create few events, but some users may want to create an event without a club. This would allow
  users to keep the competitive scene alive without a club.

## Recently Completed (No Longer Missing)

- [x] Expand authorization test coverage for all mutation paths and edge cases.
  Delivered:
    - added a dedicated integration auth matrix covering every authenticated mutation route with explicit `401` expectations
    - added `403` deny coverage across club, club-admin, tournament, tournament-player, match-scoring, player, and racket owner-boundary mutations
    - added manager/admin happy-path coverage for club, tournament, tournament-flow, and match-scoring mutations
    - covered auth-sensitive edge cases including owner-not-manageable-via-admins and tournament moves into unmanaged clubs
    - consolidated seeded auth fixtures/helpers in a dedicated coverage test to keep expansion maintainable
- [x] Implement Group and Swiss in `TennisTournamentLib` and backend start/progression support.
  Delivered:
    - backend phase creation/start/progression now supports `GROUP` and `SWISS`
    - group standings, swiss ranking snapshots, and cross-phase advancement are covered end-to-end
    - backend and lib behavior were validated together with targeted and full test runs
- [x] Tighten tournament/phase validation inputs.
  Delivered:
    - enforced `startDate <= endDate` on tournament create/update
    - validated draft phase definitions against projected entrant counts
    - blocked draft player add/remove mutations that would invalidate saved phase configuration
    - improved knockout qualifier validation messaging against projected player counts
- [x] Add user profile achievement badges for tournament wins.
  Delivered:
    - persisted a single tournament champion in `tournaments.champion_player_id`
    - populated the champion on tournament completion for knockout, group, and swiss terminal phases
    - for Group/Swiss ties on top `points`, selected the lowest `player_id` deterministically
    - moved achievement definitions into the database so names, descriptions, activation, and thresholds can change without rebuilds
    - exposed DB-backed `achievements` only on `GET /users/{id}`
    - kept external champions as tournament facts without profile achievements
- [x] JWT authentication baseline (Clerk-compatible verifier + test verifier)
- [x] CORS origin allow-list configuration
- [x] Role-based authorization for write endpoints (owner/admin model)
- [x] Auth setup and onboarding docs under `docs/`
- [x] Flyway migration scaffolding + baseline SQL (`V1__baseline.sql`)
- [x] Baseline migration executed successfully on Supabase and recorded (`docs/DB_BASELINE_STATUS.md`)
- [x] Tournament lifecycle guards for write operations (draft-only mutations + phase-order constraints)
- [x] Deterministic seeding end-to-end for knockout (backend seed contract + lib seeded participant behavior)
- [x] Harden match score submission integrity (scoreable state, both players assigned, structural score validation while keeping flexible scoring formats)
- [x] Add concurrency/idempotency guardrails for start/progression (row-level locking, round-slot uniqueness, idempotent repeated start and score replay behavior)
