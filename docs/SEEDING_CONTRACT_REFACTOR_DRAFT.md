# Seeding Contract Refactor Draft

Date: 2026-03-05
Scope: `TennisTournamentBackend` + `TennisTournamentLib`
Status: Draft only (not implemented)

## Why Change
Current seeding depends on `playerIds` list order passed from backend to lib.
That is implicit and fragile (query ordering/refactors can silently change brackets).

The goal is an explicit seeding model where seed is data, not list position.

## Proposed Contract (Target)

### Lib input model
Introduce explicit participant input shared across formats:

```kotlin
data class SeededParticipant(
    val playerId: Int,
    val seed: Int? = null
)
```

New/updated lib API:

```kotlin
fun startPhaseWithParticipants(
    participants: List<SeededParticipant>,
    phase: Phase
): List<Match>
```

Notes:
- `seed != null` means seeded.
- `seed == null` means unseeded.
- Knockout: `PARTIAL_SEEDED` uses explicit seeds and randomizes unseeded participants.
- Group/Swiss can accept the same participant model now, even if seed usage is phased in.
- `seededPlayerCount` can be removed after full migration to explicit seeds.

### Backend persistence model
Add optional seed to tournament-player association.

Table: `tournament_players`
- new column: `seed INTEGER NULL`
- constraints:
  - `CHECK (seed IS NULL OR seed > 0)`
  - `UNIQUE (tournament_id, seed)` with nulls allowed (Postgres already allows multiple nulls)

This keeps one source of truth for seeding in backend DB.

## Backend API Draft

### Existing endpoint extension
`POST /tournaments/{id}/players`

Current item:
- `playerId` or `name`

Draft extension:
- `seed: Int? = null`

Rules:
- duplicates for non-null seed in same tournament rejected with `409`.
- adding seeded player after start remains forbidden.

### New endpoint for reseeding (optional but recommended)
`PUT /tournaments/{id}/seeding`

Request:
```json
{
  "seeds": [
    { "playerId": 12, "seed": 1 },
    { "playerId": 35, "seed": 2 },
    { "playerId": 91, "seed": null }
  ]
}
```

Rules:
- allowed only in `DRAFT`.
- all listed players must belong to tournament.
- non-listed players keep current seed (or choose strict mode and require full list).

## Start Flow Draft
In backend start logic (`TournamentRepositoryImpl`), stop deriving seeding from collection order:

Current:
- `val playerIds = tournament.players.map { it.id.value }`

Draft:
1. read tournament-player rows with `seed`.
2. build `participants`.
3. call lib with explicit participants.

Recommended ordering rule before lib call:
- seeded participants sorted by `seed ASC`.
- unseeded participants in deterministic order (`playerId ASC`) for reproducibility before randomization step in lib.

## Lib Seeding Behavior Draft

### `INPUT_ORDER`
- ignores `seed`, keeps participant list order.

### `RANDOM`
- ignores `seed`, randomizes all.

### `PARTIAL_SEEDED`
- seeded participants sorted by `seed ASC`.
- unseeded participants shuffled.
- byes assigned to lowest seed numbers first.
- seeded-vs-seeded avoided in round 1 when unseeded opponents exist.

Validation:
- no duplicate `playerId`.
- no duplicate non-null `seed`.
- seeds must be positive.

## Flyway Migration Draft
Add a new backend migration (next version after current):

```sql
ALTER TABLE tournament_players
    ADD COLUMN IF NOT EXISTS seed INTEGER NULL;

ALTER TABLE tournament_players
    ADD CONSTRAINT tournament_players_seed_positive_check
    CHECK (seed IS NULL OR seed > 0);

CREATE UNIQUE INDEX IF NOT EXISTS tournament_players_tournament_id_seed_uidx
    ON tournament_players (tournament_id, seed)
    WHERE seed IS NOT NULL;
```

If constraint names may vary across envs, use `DO $$ BEGIN ... END $$` guards like current migrations.

## Compatibility Plan

### Phase 1 (non-breaking)
- backend stores seeds but still calls existing lib API by materializing ordered `playerIds`.
- lib keeps current API.

### Phase 2
- add new lib participant API and backend wiring.
- keep old API as adapter for one release.

### Phase 3
- remove old implicit order-only path after backend migration is complete.

## Tests To Add

Backend:
- add seeded players and verify duplicate seed conflict.
- reseed endpoint draft behavior (if implemented).
- start tournament uses explicit seed ordering.

Lib:
- participant validation (duplicate seed/player).
- partial seeded pairing with byes.
- deterministic behavior when seeds are fixed.

## Open Decisions
1. Should seed gaps be allowed (`1, 4, 9`) or forced contiguous (`1..N`)?
2. Should `PUT /seeding` be full-replacement or partial patch?
3. Should `INPUT_ORDER` remain public once explicit seed contract is adopted?
