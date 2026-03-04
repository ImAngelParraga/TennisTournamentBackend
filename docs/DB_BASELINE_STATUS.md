# Database Migration Status

Recorded At: 2026-03-03 16:23:51 +01:00
Repository: TennisTournamentBackend
Environment: Supabase (current configured DB)

## Command
`./gradlew.bat flywayInfo --no-daemon`

## Result
- Schema version: `2`

Applied migrations:
- `V1__baseline.sql`
  - Description: `baseline`
  - Installed on: `2026-03-03 15:38:45`
  - State: `Success`
- `V2__tournament_lifecycle_constraints.sql`
  - Description: `tournament lifecycle constraints`
  - Installed on: `2026-03-03 16:23:51`
  - State: `Success`

## Notes
- This file is a snapshot record of the current migration state.
- Add only forward migrations (`V3+`) for schema changes.
