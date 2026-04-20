# Start Here: TennisTournamentBackend

## 1) Purpose and Current Scope

This backend manages clubs, players, tournaments, phases, and matches for tennis competitions.

Current product scope:
- Knockout, Group, and Swiss phases are supported.
- Knockout remains the most battle-tested format.
- Public read APIs are open.
- Write APIs require JWT auth and role checks.
- Tournament bracket generation/progression logic is delegated to `TennisTournamentLib`.

## 2) Tech Stack

- Kotlin JVM 21
- Ktor 3 (Netty, routing, serialization, auth, CORS)
- Exposed DAO + JDBC
- Koin DI
- H2 (local/test) and PostgreSQL (production)
- JWT verification via Clerk-compatible JWKS (`jwks-rsa`)

Main config/build files:
- `build.gradle.kts`
- `settings.gradle.kts`
- `src/main/kotlin/bros/parraga/Application.kt`

## 3) Repository and Code Map

Core runtime modules:
- `src/main/kotlin/bros/parraga/modules/Routing.kt`
- `src/main/kotlin/bros/parraga/modules/Security.kt`
- `src/main/kotlin/bros/parraga/modules/HTTP.kt`
- `src/main/kotlin/bros/parraga/modules/DatabaseConfig.kt`
- `src/main/kotlin/bros/parraga/modules/Koin.kt`

Routes:
- `src/main/kotlin/bros/parraga/routes/TournamentRoute.kt`
- `src/main/kotlin/bros/parraga/routes/MatchRoute.kt`
- `src/main/kotlin/bros/parraga/routes/ClubRoute.kt`
- `src/main/kotlin/bros/parraga/routes/PlayerRoute.kt`
- `src/main/kotlin/bros/parraga/routes/UserRoute.kt`

Persistence/services:
- `src/main/kotlin/bros/parraga/services/repositories/...`
- `src/main/kotlin/bros/parraga/services/TournamentProgressionService.kt`
- `src/main/kotlin/bros/parraga/services/auth/AuthorizationService.kt`
- `src/main/kotlin/bros/parraga/db/schema/...`

## 4) External Dependency: TennisTournamentLib

The backend consumes tournament engine functionality from `TennisTournamentLib`.

What is expected from the lib:
- Knockout bracket generation
- Group single round-robin generation
- Swiss round generation and standings-based round progression
- Qualifier round computation
- Score to winner resolution (`Match.applyScore`)
- Dependency-based progression and third-place support

Local development uses composite build substitution when `../TennisTournamentLib` exists (see `settings.gradle.kts`).

## 5) AuthN/AuthZ Model

Authentication:
- JWT auth provider name: `clerk-jwt`
- Production verification: JWKS from `CLERK_ISSUER/.well-known/jwks.json`
- Test verification: local HMAC verifier

Authorization:
- Write permissions are enforced by ownership/club-admin checks.
- Club owner can manage own club and tournaments under that club.
- Club admins can also manage club/tournament/match write operations.
- `/users` write endpoints are intentionally disabled and return `403`.

Local user provisioning:
- On first authenticated write request, a local `users` row is auto-created for JWT `sub`.
- User mapping fields: `authProvider` and `authSubject`.

Key files:
- `src/main/kotlin/bros/parraga/modules/AuthConfig.kt`
- `src/main/kotlin/bros/parraga/routes/AuthUtils.kt`
- `src/main/kotlin/bros/parraga/services/auth/AuthorizationService.kt`
- `src/main/kotlin/bros/parraga/db/schema/UserEntity.kt`
- `src/main/kotlin/bros/parraga/db/schema/ClubAdminEntity.kt`

## 6) Environment Variables

Database:
- `DATABASE_URL`
- `DATABASE_DRIVER`
- `DATABASE_USER`
- `DATABASE_PASSWORD`
- `DATABASE_AUTO_CREATE`
  - defaults to `true` only when the app falls back to local H2
  - for hosted DBs (for example Supabase), set this to `false` and run Flyway migrations

Auth/CORS:
- `CLERK_ISSUER`
- `CLERK_AUDIENCE`
- `ALLOWED_ORIGINS` (comma-separated full origins)
- `AUTH_TEST_JWT_SECRET` (tests only, optional)

Flyway:
- uses `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`
- optional local alternative: Gradle properties `databaseUrl`, `databaseUser`, `databasePassword`

Behavior without DB env vars:
- App falls back to in-memory H2.

## 7) API Surface (Current Behavior)

Public reads:
- `GET /clubs`
- `GET /clubs/{id}`
- `GET /clubs/{id}/admins`
- `GET /players`
- `GET /players/{id}`
- `GET /users`
- `GET /users/{id}`
- `GET /tournaments`
- `GET /tournaments/{id}`
- `GET /tournaments/{id}/players`
- `GET /tournaments/{id}/phases`
- `GET /tournaments/{id}/matches`
- `GET /tournaments/{id}/bracket`
- `GET /matches/{id}`

Authenticated writes (with role checks where applicable):
- Clubs:
  - `POST /clubs`
  - `PUT /clubs`
  - `DELETE /clubs/{id}`
  - `POST /clubs/{id}/admins/{userId}`
  - `DELETE /clubs/{id}/admins/{userId}`
- Players:
  - `POST /players`
  - `PUT /players`
  - `DELETE /players/{id}`
- Tournaments:
  - `POST /tournaments`
  - `PUT /tournaments`
  - `DELETE /tournaments/{id}`
  - `POST /tournaments/{id}/start`
  - `POST /tournaments/{id}/phases`
  - `POST /tournaments/{id}/players`
  - `DELETE /tournaments/{id}/players/{playerId}`
- Matches:
  - `PUT /matches/{id}/score`
- Users (disabled by design):
  - `POST /users` -> `403`
  - `PUT /users` -> `403`
  - `DELETE /users/{id}` -> `403`

## 8) Local Run and Test

Run backend:
- `./gradlew run` (or `gradlew.bat run` on Windows)

Run tests (recommended on Windows):
- `GRADLE_OPTS=-Dkotlin.compiler.execution.strategy=in-process ./gradlew test --no-daemon`

If running with local lib source:
- Ensure `../TennisTournamentLib` exists and compiles.

## 9) Data Model Highlights

Important tables/entities:
- `users`
- `players`
- `clubs`
- `club_admins`
- `tournaments`
- `tournament_players`
- `tournament_phases`
- `matches`
- `match_dependencies`

Auth-related schema additions:
- `users.auth_provider`
- `users.auth_subject` (unique)
- `club_admins` join table

## 10) Error Handling Contract

Responses are wrapped in `ApiResponse(status, data, message)`.

Common error mapping:
- `400` for invalid parameters/payload
- `401` for missing/invalid token
- `403` for permission failures
- `404` for entity-not-found cases
- `409` for domain conflicts (for example duplicate player profile for same user)
- `500` for unexpected errors

## 11) Known Constraints and Risks

- Knockout is still the most mature path, but Group and Swiss are now available.
- Group uses single round robin.
- Swiss currently uses simple standings-based pairing with no rematch-avoidance rule.
- Swiss cross-phase advancement is controlled by `advancingCount` in phase config.
- If `advancingCount` is omitted, Swiss advances all players to the next phase.
- Deterministic seeding is not fully implemented.
- Tournament lifecycle/state machine is still permissive.
- Auto schema mutation is enabled by default and not a long-term production migration strategy.
- Additional concurrency/idempotency hardening is still needed for repeated start/progression calls.

## 12) Recommended First Tasks for a New Dev/Model

1. Read `MODEL_CONTEXT.md` first, then use this file only if you need more architecture detail.
2. Run tests and verify local environment.
3. Validate one full knockout flow using Postman collection + sequence guide.
4. Pick a high-priority issue from `docs/ISSUES.md`.

## 13) Other Documentation in This Folder

- `docs/AUTH_SETUP.md`: auth and env setup details.
- `docs/DB_MIGRATIONS.md`: Flyway migration workflow and Supabase setup steps.
- `docs/ISSUES.md`: prioritized backlog/checklist.
- `docs/postman/TEST_SEQUENCES.md`: manual API sequence guide.

Treat `MODEL_CONTEXT.md` as the canonical onboarding entrypoint.
