# Issues (Priority Ordered)

This list reflects the current state of both repos:

- Backend: `TennisTournamentBackend`
- Library: `TennisTournamentLib`

## P0 (Highest Priority)

- No open P0 items.

## P1 (High Priority)

- [x] Implement Group and Swiss in `TennisTournamentLib` and backend start/progression support.
  Why: `GroupService` and `SwissService` are still TODO in the lib while format types exist in shared models.

- [x] Tighten tournament/phase validation inputs.
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

- [ ] Add authenticated racket listing and management endpoints.
  Why: once racket QR history exists, stringers and owners will need a private way to list and manage their rackets
  without relying only on the public QR URL.

- [ ] Design safe racket ownership claim/link workflow.
  Why: rackets can start without a registered owner, but later should be claimable by the correct user without allowing
  incorrect self-linking.
  Missing pieces:
    - ownership invitation/approval model
    - who can initiate and approve the link
    - how claim links/tokens expire and are audited

## Recently Completed (No Longer Missing)

- [x] Add racket stringing QR history service
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
