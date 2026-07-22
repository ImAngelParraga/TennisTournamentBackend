# CONTINUITY

## 2026-07-13 - Private competitions backend
- Branch: `master` (uncommitted). Implemented backend support for user-created private competitions.
- Private tournaments: `tournaments.club_id` is nullable with `owner_user_id`, `visibility`, and `invite_code` via `V18__private_competitions.sql`; omitting `clubId` on `POST /tournaments` creates a PRIVATE owner-owned tournament, `POST /tournaments/join` joins by invite code, public tournament/profile lists hide private tournaments, private reads require owner/participant access, private tournament join requests are blocked, and public Elo application/backfill skips private tournaments.
- Leagues: added `leagues`, `league_members`, `league_matches`, and `league_rating_events` schema/entities; new `/leagues` routes support create/update/delete, invite-code join/regeneration, member-by-email add/remove, member/match lists, result recording, and owner result deletion. League Elo reuses `EloCalculator` in an isolated replay service and does not mutate public `players.rating` or `rating_events`.
- Shared player resolution extracted into `PlayerResolutionService`; tournament add-players now supports registered-user email resolution.
- Postman collection updated with private tournament and league endpoints. `MODEL_CONTEXT.md` updated for the new baseline.
- Validated with `./gradlew.bat test --tests "bros.parraga.LeagueTest" --tests "bros.parraga.PrivateTournamentTest" --no-daemon`, full `./gradlew.bat test --no-daemon`, and final `./gradlew.bat testClasses --no-daemon`.
- Remaining: run Flyway `info/migrate/validate` against the hosted database when credentials are available, then deploy for frontend smoke testing.

## 2026-07-12 - Tournament reset with rating rollback
- Branch: `master` (uncommitted). Product fix coordinated with the frontend: once a tournament is started, setup actions should be hidden and reset should be the only management action.
- Runtime rating policy changed so match Elo is no longer awarded when each match is scored. Tournament match ratings are applied in completion order only when the final phase completes, immediately before the tournament completion bonus. This avoids provisional point churn while the bracket is still editable/resettable.
- `POST /tournaments/{id}/reset` now accepts `STARTED` and `COMPLETED` tournaments, reverts all rating events tied to the tournament, clears champion/result/progress state, deletes generated matches/dependencies/groups/Swiss rankings, resets phase rounds, and returns the tournament to `DRAFT` so players and phases can be edited again.
- Tests added/updated for reset after completed matches, post-reset setup edits, no rating before completion, and rating rollback on reset. Postman reset description updated.
- Validated in this session with targeted backend tests, the full backend suite, and the web Vitest suite.

Last Updated: 2026-07-12
Repository: TennisTournamentBackend

## 2026-07-12 — Editable knockout match scores before dependent play
- Branch: `master` (uncommitted). Frontend score editing exposed a backend guard that rejected any changed payload for an already `COMPLETED` match.
- `MatchRepositoryImpl.updateMatchScore`: completed knockout matches can now be rescored while every dependent match is still unplayed (`SCHEDULED`, no winner, no score). The previous winner/loser slot is removed from dependent matches, prior match rating events are reverted, and progression is reapplied from the new score. Rescoring remains blocked once a dependent match has a result, for completed tournaments, for walkovers, and for non-knockout formats whose standings/rankings have already consumed the result.
- `UpdateMatchScoreRequest` now accepts optional `winnerId`; the repository verifies it matches the submitted score. Postman "Update Match Score" now includes the optional field in the example.
- Tests: added coverage for rescoring a completed knockout match before the dependent match is played and rejecting after the dependent match is played. Validated with `./gradlew.bat test --tests "bros.parraga.TournamentRepositoryTest" --no-daemon` and full `./gradlew.bat test --no-daemon`.

## 2026-07-07 — CORS: allow prod + Vercel preview origins
- The deployed frontend (`https://tennis-tournaments.vercel.app`) was blocked by CORS — the service `ALLOWED_ORIGINS` was localhost-only. Added the prod origin to `ALLOWED_ORIGINS` on the Cloud Run service (new revision `-00008`) and to `cloudrun.env.yaml` so a manual full deploy won't revert it.
- `modules/HTTP.kt`: added a scoped `allowOrigins { … }` predicate matching `^https://tennis-tournaments(-<hash>)?\.vercel\.app$` (case-insensitive) so Vercel preview/branch deploys work without listing each dynamic subdomain. Anchored + prefix-scoped, so arbitrary `*.vercel.app` and lookalike/suffix domains are rejected. Verified locally: prod/preview/git-branch/localhost allowed; `evil.vercel.app`, `tennis-tournaments.evil.com`, `…vercel.app.evil.com` blocked. Full test suite passes.
- Resolves the follow-up flagged in the CI/CD entry below.

## 2026-07-07 — CI/CD: auto-deploy to Cloud Run on push to master
- New GitHub Actions workflow `.github/workflows/deploy.yml`: on push to `master` (also `workflow_dispatch`) it runs `test` → `flywayMigrate` (gate) → build/push image → `gcloud run deploy`. Concurrency-serialized; a failed migration aborts the deploy.
- **Keyless auth (Workload Identity Federation)** — no credentials stored in GitHub. GCP side (provisioned via gcloud): deployer SA `github-deployer@tennis-tournament-490501.iam.gserviceaccount.com` with `roles/run.admin`, `roles/artifactregistry.writer`, `roles/secretmanager.secretAccessor`, plus `roles/iam.serviceAccountUser` on `tennis-backend-runner`; WIF pool `github-pool` + OIDC provider `github-provider` locked to repo `ImAngelParraga/TennisTournamentBackend`.
- **Image build via Ktor `publishImage` (Jib), no Dockerfile.** Added a `ktor { docker { … } }` block to `build.gradle.kts` targeting `europe-west1-docker.pkg.dev/tennis-tournament-490501/tennis-tournament-backend/tennis-tournament-backend`; tag = short git sha, auth via short-lived OAuth token (`REGISTRY_TOKEN`).
- **Migration gate** reads the DB password from Secret Manager (`tennis-backend-database-password`) and runs `flywayMigrate` against the prod Supabase DB before deploying — satisfies the `docs/ISSUES.md` deployment-pipeline backlog item.
- Deploy is an **image-only update**: service env vars, secret bindings, runtime SA, and scaling stay as configured on the Cloud Run service (no config drift into the workflow).
- Follow-up (not done here): `ALLOWED_ORIGINS` on the running service still lists only localhost — add the prod frontend origin before the frontend can call the API.

## 2026-07-02 — Manual-testing personas: claim-by-email + seeded admin/club-manager + contact-request delete
- Branch: `master` (uncommitted). Goal: end-to-end manual testing through the real frontend (see new `docs/MANUAL_TESTING.md`).
- **Claim-by-email** (`UserRepositoryImpl.claimByEmail`): on first login, if no row matches the JWT subject but an unclaimed row (`auth_subject IS NULL`) matches the token's verified email, the subject is bound to that row (provider set, name filled if null). Rows bound to another subject are never claimed. Also removes the previous guaranteed unique-email crash when logging in with an email that exists on an unclaimed row.
- **Seed personas** (`SeedData.kt`): `platform-admin` (email `SEED_ADMIN_EMAIL`, default `admin+clerk_test@example.com`, role PLATFORM_ADMIN) and `club-manager` (email `SEED_CLUB_MANAGER_EMAIL`, default `club+clerk_test@example.com`), both `auth_subject = null`. Club ownership moved from `seed-owner` (still the idempotency sentinel, now a club admin) to `club-manager`. Also seeds 3 pending `club_contact_requests` for the /admin review flow.
- New `DELETE /club-contact-requests/{id}` (platform admin, 204/403/404) — the operator's "handled" action after provisioning a club from an inquiry.
- Frontend (separate repo) adds role-gated `/admin` page: list inquiries, create club for a username, delete handled inquiry.
- Tests: `UserTest` claim + no-steal cases; `ClubContactRequestTest` delete cases. Full suite passes (97 tests). Postman + `MODEL_CONTEXT.md` updated.
- One-time operator setup: create the two email+password users in the Clerk dev dashboard (`well-whippet-40`); H2 resets per restart and personas re-claim automatically.

## 2026-07-02 — matchWins on user reads (frontend ranking metric)
- `domain/User` gains `matchWins: Int = 0` (count of matches where the user's linked player is `winner`; winner is only set on COMPLETED/WALKOVER). Populated on `GET /users` (one grouped query: matches ⋈ players on winner, grouped by `players.user_id` — no N+1), `GET /users/{id}`, `GET /users/by-username/{username}`, and `GET /users/me`. Omitted from JSON when 0 (defaults not serialized).
- No schema change. Frontend (separate repo) ranks the /profile "Jugador" table by `matchWins` (was achievements count, which the list endpoint never included — ranking was effectively alphabetical).
- Tests: `UserTest` gains `should return match wins on user reads` (list 2/1/0 + by-username detail). Full suite passes. Postman "Get Users" description updated.

## 2026-07-02 — Club contact requests (onboarding inquiries from the website form)
- Branch: `master` (uncommitted). The frontend's "Para clubes" CTAs now open a contact form instead of a mailto; this backend records those inquiries.
- New table `club_contact_requests` via Flyway `V16__club_contact_requests.sql` (`club_name`, `contact_name`, `email`, `phone?`, `message?`, `created_at`). Mirrored in `ClubContactRequestEntity.kt`, `domain/ClubContactRequest`, added to `DatabaseTables`.
- New `routes/ClubContactRoute.kt`: public anonymous `POST /club-contact-requests` (validates non-blank club/contact/email, loose email regex, length caps incl. 4000-char message → 400 via `IllegalArgumentException`) and platform-admin `GET /club-contact-requests` (sorted newest first). Repository `ClubContactRequestRepository[Impl]` under `services/repositories/club/`, wired in Koin + Routing.
- Tests: new `ClubContactRequestTest` (201 create, 400 blank/invalid email, GET 401/403/200-admin). Full suite passes (93 tests).
- Remaining: `flywayMigrate`/`flywayValidate` against hosted DB (V15 also still pending); no operator notification (email/webhook) — inquiries are pull-only via the admin GET/Postman.

## 2026-07-02 — Platform-admin role; clubs are provisioned manually (no self-service creation)
- Branch: `master` (uncommitted). Product decision: clubs are onboarded personally by the platform operator; users can no longer create clubs from the website/API.
- New `users.role` column (`USER` | `PLATFORM_ADMIN`) via Flyway `V15__user_platform_role.sql` (default `USER`, CHECK constraint). Mirrored in `UsersTable`/`UserDAO` (Exposed default keeps H2 test/dev schema working) and `domain/User` (`UserRole` enum). The role is assigned via manual SQL only, never via API: `UPDATE users SET role = 'PLATFORM_ADMIN' WHERE auth_subject = '<clerk sub>';` (operator must have signed in once so the row exists).
- `AuthorizationService`: new `isPlatformAdmin`/`requirePlatformAdmin` (403 `ForbiddenException`, 404 on unknown user).
- `POST /clubs` now requires `PLATFORM_ADMIN` and `CreateClubRequest` gains required `ownerUserId` (admin creates the club on behalf of the club's user; unknown id → 404). `ClubRepository.createClub(request, ownerUserId)` → `createClub(request)`. `DELETE /clubs/{id}` is now platform-admin only (owners/admins keep `PUT /clubs` and admin add/remove). `deleteClub` now clears `club_admins` rows first — the FK has no cascade, so deleting a club with admins used to 500 (latent bug surfaced by new tests).
- `GET /users/me` now returns `role` and `managedClubIds` (owned ∪ administered club ids, sorted) via new `UserRepository.getMe`; other user reads keep defaults (`USER`/`[]`). Frontend (separate repo) gates host UI from this single call.
- Tournament hosting was already correctly gated (`POST /tournaments` → `requireClubManager`); unchanged.
- Tests: new `PlatformAdminTest` (403 regular create, 201 admin create for other user, 404 unknown owner, 403 owner delete, 204 admin delete, `/users/me` role+managedClubIds for owner/club-admin/regular/platform-admin). `MutationAuthorizationCoverageTest` gained outsider `POST /clubs` 403; existing `CreateClubRequest` call sites updated. Full suite passes: `./gradlew.bat test --no-daemon` (90 tests).
- Postman: Create Club (platform-admin note + `ownerUserId`), Delete Club note, Get My User note; both zero-state scenarios' Create Club steps annotated (first run 403s, promote user id 1 via SQL, re-run) and bodies gain `ownerUserId: 1`. `MODEL_CONTEXT.md` auth section updated.
- Remaining: run `flywayInfo`/`flywayMigrate`/`flywayValidate` against the hosted DB when creds are available; promote the operator's user in prod after first sign-in.

## 2026-07-01 — Dev seed data system (local H2 only)
- New `db/seed/SeedData.kt` + `modules/SeedConfig.kt`; `configureSeeding()` wired as the last call in `Application.module()` (after `configureDatabase()`). Not called from `testModule()`.
- Gating: runs only when `SEED_DATA=true`, and refuses any non-H2 DB unless `SEED_FORCE=true` (guard via new `usingLocalH2Fallback()` in `DatabaseConfig.kt`, mirroring the existing fallback check). Idempotent via sentinel user `seed-owner`. Failures are logged and swallowed so startup never blocks; each scenario is independently try/caught.
- Base entities (users/club/players) inserted via Exposed DAOs; all tournament lifecycle state is produced by driving the real repositories (`TournamentRepository.createTournament/addPlayersToTournament/createPhase/startTournament`, `MatchRepository.updateMatchScore`, `TournamentJoinRequestRepository.createJoinRequest`) so bracket/progression/champion come from `TennisTournamentLib`, not hand-inserted.
- Scenarios: DRAFT knockout (+2 pending join requests), STARTED knockout (8 players, round 1 scored so bracket advanced), COMPLETED knockout (4 players, all matches scored → champion + status COMPLETED), GROUP (2×4) and SWISS (6) samples started.
- Also made the HTTP port env-configurable: `PORT` (defaults 8080; used for local verification and matches Cloud Run's injected `PORT`). `run-local.ps1` now sets `SEED_DATA=true`.
- No schema/migration change (seed is data, not schema; nothing added to `DatabaseTables.kt`). Verified end-to-end by booting on `PORT=8090 SEED_DATA=true` against H2 and hitting `/tournaments` + `/tournaments/{id}/matches`. `./gradlew.bat test --no-daemon` passes.

## 2026-06-30 — Public endpoint: tournaments a user is registered in
- New public read `GET /users/{id}/tournaments` → `UserRepository.getUserTournaments` returns `List<TournamentBasic>` for the tournaments the user's linked player is registered in (rows in `tournament_players`), sorted by start date ascending.
- Behavior: 404 if the user does not exist (`UserDAO[id]`); empty list if the user has no linked player or no registrations. No date filtering server-side — the frontend carousel narrows to upcoming.
- Impl mirrors `getUserMatchActivity`: find player by `PlayersTable.userId`, collect tournament ids from `TournamentPlayersTable`, load via `TournamentDAO.find { TournamentsTable.id inList ... }.toBasic()`.
- No schema/migration change (read-only over existing tables).
- Frontend (separate repo) consumes this for the non-owner profile Overview carousel (`useUserTournamentsQuery`).
- Tests: `UserTest` passes — added registered-sorted, empty-when-no-player, and 404 cases. Postman updated with "Get User Tournaments".

## 2026-06-29 — User profile name/image (frontend-only edit, no webhook)
- Branch: `master` (uncommitted).
- Added `name` and `image_url` to `users` via Flyway `V14__user_name_and_image.sql`; mirrored in `UsersTable`/`UserDAO`, `domain/User`, and `toDomain`. Both nullable, returned by all `/users` reads.
- New `PATCH /users/me` (clerk-jwt): self-service edit of `name`/`imageUrl` via `UpdateProfileRequest` → `UserRepository.updateOwnProfile`. The blanket `/users` write 403 still applies to POST/PUT/DELETE; PATCH/me is the only allowed self-write.
- `findOrCreateByAuthSubject` seeds `name` from the JWT preferred-name on first create, so name is populated on signup without any webhook.
- Decision: editing happens **only** in the frontend UI (logged-in → JWT → PATCH/me). Clerk's own account modal is hidden, so there is no out-of-app edit path to mirror. The earlier `POST /users/sync` + webhook approach was removed (no server-to-server upsert, no shared secret).
- Caveat to handle in ops: disable/restrict the Clerk hosted Account Portal in the dashboard so users can't edit Clerk profile outside the app (which would drift the DB, since there is no webhook).
- Tests: `UserTest` passes (`./gradlew.bat test --tests "bros.parraga.UserTest" --no-daemon`). Postman updated with "Update My Profile".
- Remaining: run `flywayMigrate`/`flywayValidate` against the hosted DB when creds are available; add a test for PATCH/me.

## 2026-06-29 — Profile URLs keyed by username (no DB ids in URLs)
- Added `GET /users/by-username/{username}` → `UserRepository.getUserByUsername` (find by unique `username`, 404 via `NotFoundException` if absent, includes achievements). Route sits before `/{id}/...` but doesn't collide (literal first segment).
- Rationale: sequential DB ids in profile URLs leak row counts / allow enumeration; `username` is unique (DB index) and not sequential.
- Frontend (separate repo) now routes `/users/{username}` (the `[id]` segment carries the username), resolves the user via this endpoint, then uses the returned numeric `id` only for sub-resource calls (`/users/{id}/rackets|trainings|profile-calendar|matches`). `/profile` redirects to `/users/{me.username}`.
- Postman: added "Get User By Username". `UserTest` still passes.
- Username is now a slug of the name (`generateUniqueUsername` -> lowercase/dash, `-2`/`-3` on collision, replacing the old `_`+UUID scheme). `updateOwnProfile` regenerates the username from the new name on every name change (excluding the user's own row), so the profile URL tracks the name. Frontend follows the returned `username` with a redirect after editing, and displays `@username` (== URL) instead of a separate client-side slug.
- Existing rows keep their old usernames until their owner next edits their name.
- Remaining: add a test for the by-username lookup (200 + 404) and for username regeneration on name change.

## Update Rule
Update this file after each meaningful implementation/review/change in this repo.
Include: branch, uncommitted state, what changed, what remains.

## Current State
- Branch: `master`
- Working tree status before this update: merging `feat/tournament-join-requests` into updated `master`
- Main documentation entrypoint: `MODEL_CONTEXT.md`
- Prioritized backlog: `docs/ISSUES.md`
- Local implementation changes after this update:
  - modified: `CONTINUITY.md`
  - modified: `docs/postman/TennisTournamentBackend.postman_collection.json`
  - modified: `src/main/kotlin/bros/parraga/db/schema/TrainingEntity.kt`
  - modified: `src/main/kotlin/bros/parraga/domain/TrainingDtos.kt`
  - modified: `src/main/kotlin/bros/parraga/routes/RoutingUtils.kt`
  - modified: `src/main/kotlin/bros/parraga/routes/TrainingRoute.kt`
  - modified: `src/main/kotlin/bros/parraga/routes/UserRoute.kt`
  - modified: `src/main/kotlin/bros/parraga/services/repositories/training/TrainingRepository.kt`
  - modified: `src/main/kotlin/bros/parraga/services/repositories/training/TrainingRepositoryImpl.kt`
  - modified: `src/main/kotlin/bros/parraga/services/repositories/training/dto/CreateTrainingRequest.kt`
  - modified: `src/main/kotlin/bros/parraga/services/repositories/training/dto/UpdateTrainingRequest.kt`
  - modified: `src/main/kotlin/bros/parraga/services/repositories/user/UserRepository.kt`
  - modified: `src/main/kotlin/bros/parraga/services/repositories/user/UserRepositoryImpl.kt`
  - modified: `src/test/kotlin/bros/parraga/MutationAuthorizationCoverageTest.kt`
  - modified: `src/test/kotlin/bros/parraga/TrainingRepositoryTest.kt`
  - modified: `src/test/kotlin/bros/parraga/UserTest.kt`
  - added: `src/main/kotlin/bros/parraga/services/repositories/user/dto/ProfileCalendarResponse.kt`
  - added: `src/main/resources/db/migration/V12__user_trainings_visibility.sql`

## Recent Completed Work
- (uncommitted in current session) Merged, migrated, and deployed tournament join requests to production:
  - merged `feat/tournament-join-requests` into `master` with merge commit `f2aee1d`
  - pushed `master` to `origin/master`
  - ran Flyway against the configured hosted database: `flywayInfo` showed pending versions 9-13, `flywayMigrate` completed, `flywayValidate` passed, and final `flywayInfo` reported schema version 13
  - built the container image locally with Jib tar as a deployment dry-run, then pushed Artifact Registry image `europe-west1-docker.pkg.dev/tennis-tournament-490501/tennis-tournament-backend/tennis-tournament-backend:f2aee1d`
  - deployed Cloud Run service `tennis-tournament-backend` in `europe-west1` to revision `tennis-tournament-backend-00004-vm6`, serving 100% traffic
  - verified live public `GET /clubs` at `https://tennis-tournament-backend-639388080916.europe-west1.run.app/clubs` returned `SUCCESS`
  - after committing this deployment record, rebuilt and redeployed the branch-tip image again so Cloud Run serves the final `master` contents rather than the pre-record commit
- (uncommitted in current session) Merged tournament join request workflow into `master` after reconciling with the user training history work already on `origin/master`:
  - preserved the existing training routes and added tournament join request routing
  - renumbered the join-request Flyway migration from `V10__tournament_join_requests.sql` to `V13__tournament_join_requests.sql` because `master` already contains `V10__user_trainings.sql`, `V11__user_trainings_duration_minutes.sql`, and `V12__user_trainings_visibility.sql`
- (uncommitted in current session) Implemented tournament join request workflow:
  - added `tournament_join_requests` schema/entity/domain and Flyway migration `V13__tournament_join_requests.sql`
  - added authenticated player request/withdraw/my-request endpoints
  - added manager list/accept/reject/allow-resubmit endpoints gated by existing club owner/admin authorization
  - auto-creates a missing user player profile from request `playerName` or username
  - accepted requests add the player to `tournament_players`; rejected requests get a 7-day cooldown that managers can unlock
  - manual manager add marks a matching pending request `ACCEPTED`; tournament start marks pending requests `EXPIRED`
  - updated Postman collection and model context with new endpoints
  - validated with `./gradlew.bat test --tests "bros.parraga.TournamentJoinRequestTest" --console=plain --quiet --warning-mode=summary --no-daemon` (pass)
- (uncommitted in current session) Added training visibility plus combined profile calendar support for richer user profile pages:
  - added `TrainingVisibility { PUBLIC, PRIVATE }` to training DTOs, persistence mapping, and create/update payloads
  - added Flyway migration `V12__user_trainings_visibility.sql` to backfill existing rows to `PRIVATE`, enforce defaults, and constrain allowed values
  - kept existing owner training CRUD routes intact while extending owner responses with `visibility`
  - added public `GET /users/{id}/trainings?from&to` returning only PUBLIC sessions and applied a shared inclusive 93-day guardrail to owner/public training range reads
  - added public `GET /users/{id}/profile-calendar` plus authenticated `GET /users/me/profile-calendar` combining scheduled/live/completed/walkover matches with training sessions into one mini-calendar response
  - profile calendar buckets match instants into local dates using optional `timezone` (default UTC), sorts events by date/time/id, and exposes per-day counts for matches vs trainings
  - public profile calendar includes all user match events plus only PUBLIC trainings; owner profile calendar includes all matches plus PUBLIC/PRIVATE trainings
  - broadened bad-request handling for malformed JSON/content transformation errors so required request fields like training `visibility` return `400` instead of bubbling to `500`
  - updated Postman examples for the new training/profile calendar endpoints and visibility-aware payloads
  - expanded integration coverage for training visibility defaults/filtering, owner/public training reads, profile calendar visibility rules, timezone bucketing, sort order, `/users/me/profile-calendar` auth, and 93-day range validation
  - validated with:
    - `source "$HOME/.sdkman/bin/sdkman-init.sh" && sh ./gradlew test --no-daemon --tests "bros.parraga.TrainingRepositoryTest" --tests "bros.parraga.UserTest" --tests "bros.parraga.MutationAuthorizationCoverageTest"` (pass)
    - `source "$HOME/.sdkman/bin/sdkman-init.sh" && sh ./gradlew test --no-daemon` (pass)
- (uncommitted in current session) Changed training retrieval from month-based lookup to explicit date ranges:
  - `GET /users/me/trainings` now expects `from=YYYY-MM-DD&to=YYYY-MM-DD` instead of `month=YYYY-MM`
  - replaced the training response wrapper with `UserTrainingRangeResponse(from, to, calendarDays, trainings)`
  - added route-level local-date query parsing plus `from <= to` validation in `RoutingUtils.kt`
  - updated the training repository contract/implementation to query inclusive `training_date` ranges instead of month boundaries
  - expanded integration coverage for date-range reads, cross-month ranges, invalid date format, reversed ranges, and existing CRUD behavior
  - updated the Postman training read example to use the new `from`/`to` query parameters
  - validated with:
    - `source "$HOME/.sdkman/bin/sdkman-init.sh" && sh ./gradlew test --no-daemon --tests "bros.parraga.TrainingRepositoryTest" --tests "bros.parraga.MutationAuthorizationCoverageTest"` (pass)
- (uncommitted in current session) Reviewed `d8759cd feat(user): add training history management` and extended tennis training entries with optional duration minutes:
  - review outcome: the commit cleanly introduced owner-scoped training CRUD/month views; the follow-up gap for frontend-ready session length was the missing duration field
  - added Flyway migration `V11__user_trainings_duration_minutes.sql` to append nullable `duration_minutes` with a positive-value DB check
  - extended training DAO/domain/request DTOs and repository write logic with optional `durationMinutes`
  - added repository validation so provided durations must be greater than zero
  - updated training integration/auth coverage tests and Postman examples to include duration-aware payloads and assertions
  - validated with:
    - `sh ./gradlew test --no-daemon --tests "bros.parraga.TrainingRepositoryTest" --tests "bros.parraga.MutationAuthorizationCoverageTest"` (pass)
- (uncommitted in current session) Extended training history with owner update/delete flows and re-evaluated the local Qwen helper:
  - added `PUT /users/me/trainings/{trainingId}` and `DELETE /users/me/trainings/{trainingId}` following the existing owner-subresource route style used by rackets
  - added `UpdateTrainingRequest` plus repository methods for update/delete with owner checks and `204 No Content` deletes
  - update rejects empty payloads, validates `trainingDate` format, and normalizes blank notes to `null`
  - expanded integration coverage for successful training update/delete, invalid update payloads, and cross-user `403` owner-boundary behavior
  - expanded mutation authorization coverage for unauthenticated `PUT`/`DELETE` on trainings
  - validated with:
    - `./gradlew.bat test --no-daemon --tests "bros.parraga.TrainingRepositoryTest" --tests "bros.parraga.MutationAuthorizationCoverageTest"` (pass)
  - local Qwen endpoint observations during this extension:
    - planning output was directionally useful on route shape (`PUT`/`DELETE` under the owner resource) but still arrived wrapped in leaked `<think>` text
    - implementation drafting was materially weaker than planning: it drifted into generic patterns like `UUID`, `Result`, and extra non-repo assumptions instead of the repo's actual `Int` ids, exception flow, and Ktor/Exposed conventions
    - even on final-change summarization it again emitted only a truncated reasoning preamble and no usable final answer within the token budget
    - current best-fit role remains low-trust helper for rough drafts or compression after a stronger model has already constrained the task and source context
- (uncommitted in current session) Implemented owner training history + monthly calendar MVP and evaluated the local Qwen helper behavior:
  - added authenticated training routes under `/users/me/trainings` for monthly reads (`GET ?month=YYYY-MM`) and creation (`POST` with `trainingDate` + optional `notes`)
  - added `user_trainings` persistence via Flyway migration `V10__user_trainings.sql`, Exposed DAO mapping, Koin wiring, and a dedicated training repository/route
  - monthly response returns both ordered training entries and compact `calendarDays` counts so the frontend can render a small month view without extra grouping work
  - added query/date validation for `month` (`YYYY-MM`) and `trainingDate` (`YYYY-MM-DD`)
  - updated Postman collection with training requests
  - added integration coverage for training creation, month filtering, calendar counts/order, auth requirement, invalid month/date handling, and `401` mutation coverage for training creation
  - validated with:
    - `./gradlew.bat test --no-daemon --tests "bros.parraga.TrainingRepositoryTest" --tests "bros.parraga.MutationAuthorizationCoverageTest"` (pass)
  - local Qwen endpoint observations during this task:
    - `GET /v1/models` worked and confirmed local model id `Qwen3.6-27B-IQ4_XS.gguf`
    - `/v1/chat/completions` timed out on a repo-context design prompt, so the model was only practical through smaller `/v1/completions` prompts
    - completion outputs repeatedly leaked `<think>` blocks despite explicit instructions not to, which makes its raw output noisy for direct user-facing use
    - on a generic route-design prompt it suggested `/users/{userId}/training-history` endpoints, which missed this repo's `/users/me/...` convention; useful for rough drafting, not for trusted repo-specific decisions without review
- (uncommitted in current session) Added public user match activity and match completion timestamps for profile calendars:
  - added Flyway migration `V9__match_completed_at.sql` to persist `matches.completed_at`, backfill terminal matches, and index `completed_at` plus match player foreign keys
  - extended `Match`/`MatchDAO` mapping with `completedAt`
  - set `completedAt` when score submission completes a match and when walkovers are generated during phase execution
  - added public `GET /users/{id}/matches?from&to` returning modal-ready match activity for the user's linked player profile with tournament, phase, opponent, score, and win/loss context
  - added authenticated `GET /users/me` for frontend self-identification without overloading public user detail routes
  - added request validation for ISO-8601 `from`/`to` query params with a max 93-day range guardrail
  - updated Postman collection with `Get My User` and `Get User Match Activity`
  - added user and tournament test coverage for `/users/me`, user match activity ranges, completed-match timestamps, and walkover timestamps
  - validated with:
    - `./gradlew.bat test --no-daemon --tests "bros.parraga.UserTest" --tests "bros.parraga.TournamentRepositoryTest"` (pass)
    - `./gradlew.bat test --no-daemon` (pass)
- (uncommitted in current session) Deployed the backend to public Cloud Run against Supabase:
  - enabled the required GCP deployment APIs and created Artifact Registry repo `tennis-tournament-backend` in `europe-west1`
  - created runtime service account `tennis-backend-runner` and Secret Manager secret `tennis-backend-database-password`
  - built and pushed image `europe-west1-docker.pkg.dev/tennis-tournament-490501/tennis-tournament-backend/tennis-tournament-backend:888aaa0`
  - added non-secret Cloud Run env config file `cloudrun.env.yaml` for repeatable deploy/update commands
  - deployed public Cloud Run service `tennis-tournament-backend` with Clerk issuer, localhost dev CORS origins, Supabase JDBC settings, and `DATABASE_AUTO_CREATE=false`
  - corrected the runtime DB user to the Supabase pooler username and refreshed the secret payload so password auth works in Cloud Run
  - verified the live public endpoint with `GET /clubs` returning success from `https://tennis-tournament-backend-639388080916.europe-west1.run.app/clubs`
- (uncommitted in current session) Simplified Clerk backend integration for deployment:
  - made `CLERK_AUDIENCE` optional in production auth config while keeping `test-audience` as the default in test mode
  - updated JWT verifier setup so production only enforces `aud` when `CLERK_AUDIENCE` is explicitly configured
  - this allows the frontend to use the default Clerk session token without first creating a custom JWT template/audience contract
  - validated with:
    - `./gradlew.bat test --no-daemon --tests "bros.parraga.AuthorizationFlowTest" --tests "bros.parraga.MutationAuthorizationCoverageTest"` (pass)
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
