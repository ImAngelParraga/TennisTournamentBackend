# Manual testing: seeded admin + club-manager accounts

End-to-end manual testing of club/tournament management and club onboarding, through the real
frontend against a locally seeded backend.

## How it works

The seed (`db/seed/SeedData.kt`, runs when `SEED_DATA=true` on the local H2 fallback) creates two
**claimable personas**: local user rows with a known email and `auth_subject = NULL`. On the first
real Clerk sign-in whose verified email matches, the backend binds that Clerk subject to the row
(`UserRepositoryImpl.claimByEmail`). No Clerk user IDs are ever copied into the seed, and because
the H2 database resets on every backend restart, the personas simply re-claim on your next login.

| Persona | Username | Default email | What it can do |
| --- | --- | --- | --- |
| Platform admin | `platform-admin` | `admin+clerk_test@example.com` | `/admin`: review club contact requests, create clubs (`POST /clubs`), delete handled requests |
| Club manager | `club-manager` | `club+clerk_test@example.com` | Owns "Seed Tennis Club": `/host`, edit club, manage club admins, create/start/score tournaments, accept join requests |

Override the emails with `SEED_ADMIN_EMAIL` / `SEED_CLUB_MANAGER_EMAIL` env vars if your Clerk test
accounts use different addresses.

## One-time setup (Clerk dev dashboard)

1. Open the Clerk dashboard for the dev instance (`well-whippet-40.clerk.accounts.dev` — the same
   instance the frontend `.env.local` publishable key points at).
2. Create two users with **email + password**:
   - `admin+clerk_test@example.com`
   - `club+clerk_test@example.com`
   `+clerk_test` addresses are Clerk test users — no real mailbox needed; if a verification code is
   ever prompted, it is `424242`.

## Per-session

1. Backend: `./run-local.ps1` (sets `CLERK_ISSUER`, `ALLOWED_ORIGINS`, `SEED_DATA=true`; H2 in-memory).
2. Frontend: `npm run dev` in `../tennis-tournament` (`.env.local` already points
   `NEXT_PUBLIC_API_BASE_URL` at `http://localhost:8080`).

## Walkthroughs

**Club manager** (sign in as `club+clerk_test@example.com`):
- `/host` → "Seed Tennis Club" appears (owner) → Gestionar.
- "Spring Open (Draft)" has 2 pending join requests → open the tournament → accept/reject them.
- Create a tournament, add players, add a phase, start it, score matches.
- "Summer Slam (In Progress)" is mid-bracket for scoring/progression checks; groups + Swiss samples too.

**Platform admin** (sign in as `admin+clerk_test@example.com`):
- Go to `/admin` (not in the nav — direct URL; the page is role-gated and every call is re-authorized
  server-side with `requirePlatformAdmin`).
- Three seeded club contact requests are listed. "Accept" one: create the club for an existing
  username (e.g. `club-manager`), then delete the handled request.
- The new club immediately shows up in `/host` for its owner.

## Notes

- Backend restart = fresh H2 = reseed; sign-ins re-claim the personas automatically.
- The claim only ever fires for rows with `auth_subject IS NULL`; rows bound to another Clerk
  subject are never claimed.
- API-level testing without the frontend: Postman collection scenarios (`docs/postman/`) still work
  with generated test JWTs when the server runs with the test verifier.
