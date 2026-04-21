# User Profile Achievement Badges Draft

## Goal
Add profile achievements that are defined in the database instead of hardcoded in Kotlin.

This allows achievements to be added, removed, renamed, re-described, activated, deactivated, or threshold-tuned without rebuilding and redeploying the backend.

## Product Decisions
- achievements are predefined by product/ops data, not generated ad hoc in code
- `GET /users/{id}` includes achievements
- `GET /users` stays unchanged
- only registered users receive profile achievements
- invalidated results do not auto-revoke achievements in the MVP
- a future admin flow may manually recompute tournament champions if needed

## Implemented Shape
### Tournament result source of truth
The backend persists a single tournament champion on the tournament itself:
- `tournaments.champion_player_id`

This keeps tournament result lookup simple and cheap.

### Achievement definitions
Achievement definitions now live in the `achievements` table.

Current table shape:
- `id`
- `key`
- `name`
- `description`
- `rule_type`
- `threshold`
- `active`

Current supported `rule_type` values:
- `TOURNAMENT_WINS_AT_LEAST`
- `MATCH_WINS_AT_LEAST`
- `MATCHES_PLAYED_AT_LEAST`

This means you can add rows like:
- first tournament win
- two tournament wins
- twenty match wins
- fifty match wins
- ten matches played

without changing backend code, as long as the achievement uses one of the supported rule types.

### User response
`User` now includes:
- the existing user fields
- `achievements: List<Achievement> = emptyList()`

`Achievement` is now a DB-backed response object:
- `id`
- `key`
- `name`
- `description`

This keeps the profile payload descriptive enough for frontend rendering without requiring a second lookup.

## Winner Resolution
Champion is resolved from the terminal phase when the tournament moves to `COMPLETED`.

### Knockout
- champion = final match winner

### Group
- collect players with the maximum final `points`
- if one player has top points, that player is champion
- if several players tie on top points, pick the lowest `player_id` deterministically

### Swiss
- collect players with the maximum final `points` from the final Swiss ranking snapshot
- if one player has top points, that player is champion
- if several players tie on top points, pick the lowest `player_id` deterministically

### Mixed formats
Champion always comes from the terminal phase.

Examples:
- `GROUP -> KNOCKOUT`: champion is the knockout winner
- `SWISS -> KNOCKOUT`: champion is the knockout winner
- `GROUP` only: champion comes from final Group standings
- `SWISS` only: champion comes from final Swiss standings

## Profile Achievement Rule
- if the persisted champion player is linked to a user through `players.user_id`, that user can unlock achievement definitions based on tournament wins
- if the champion is an external player, no user profile achievement is awarded

Achievement resolution is data-driven:
1. compute the user player's stats
2. load active achievement definitions from the DB
3. return the definitions whose rule/threshold conditions are satisfied

## Why This Is Better Than Hardcoded Achievement Enums
The earlier enum-based approach made every new achievement a code change.

That was wrong for the product direction because:
- changing badge names required a deploy
- changing thresholds required a deploy
- enabling/disabling achievements required a deploy
- adding multiple milestones like 20 wins and 50 wins required code edits instead of DB rows

The DB-backed model fixes that while still keeping the achievement evaluation logic small and explicit.

## Current Limit
Achievement rule categories are still implemented in code.

So:
- adding a new row for `MATCH_WINS_AT_LEAST` works without rebuild
- inventing a brand new rule family would still require code support

That is an acceptable MVP tradeoff and much better than hardcoding every individual achievement.

## Relevant Files
- `src/main/kotlin/bros/parraga/domain/User.kt`
- `src/main/kotlin/bros/parraga/db/schema/AchievementEntity.kt`
- `src/main/kotlin/bros/parraga/db/schema/UserEntity.kt`
- `src/main/kotlin/bros/parraga/db/schema/TournamentEntity.kt`
- `src/main/kotlin/bros/parraga/services/repositories/user/UserRepositoryImpl.kt`
- `src/main/kotlin/bros/parraga/services/TournamentProgressionService.kt`
- `src/main/resources/db/migration/V6__tournament_champion_player.sql`
- `src/main/resources/db/migration/V7__achievement_definitions.sql`
